package io.github.faridkosarifard.syncsafe.fixtures;

import io.github.faridkosarifard.syncsafe.read.HadoopSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.hadoop.HadoopFileIO;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.parquet.Parquet;

/** Builds local Delta tables, including ones that carry on-disk deletion vectors. */
public final class DeltaFixtures {
  private static final String DATA_FILE = "part-00000.parquet";
  private static final String DV_FILE = "deletion_vector.bin";

  private DeltaFixtures() {}

  /** All rows live: a single add action and no deletion vector. */
  public static Path createDataOnly(Path location, List<CustomerRow> rows) throws IOException {
    Files.createDirectories(location);
    long size = writeParquet(location.resolve(DATA_FILE), rows);
    long now = System.currentTimeMillis();
    String add = addAction(DATA_FILE, size, now, rows.size(), null);
    writeLog(location, 0, protocolAndMetadata(now) + add + "\n");
    return location;
  }

  /**
   * Version 0 is an append of every row; version 1 adds a deletion vector for {@code deletedIds}
   * without rewriting the Parquet file.
   */
  public static Path createWithDeletionVectors(
      Path location, List<CustomerRow> rows, Set<Long> deletedIds) throws IOException {
    Files.createDirectories(location);
    long size = writeParquet(location.resolve(DATA_FILE), rows);
    long now = System.currentTimeMillis();
    String add = addAction(DATA_FILE, size, now, rows.size(), null);
    writeLog(location, 0, protocolAndMetadata(now) + add + "\n");

    List<Long> positions = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      if (deletedIds.contains(rows.get(i).id())) {
        positions.add((long) i);
      }
    }
    if (positions.isEmpty()) {
      return location;
    }
    DeletionVectorIO.Written dv = DeletionVectorIO.write(location.resolve(DV_FILE), positions);
    String dvJson =
        "{\"storageType\":\"p\",\"pathOrInlineDv\":\""
            + jsonEscape(HadoopSupport.uri(location.resolve(DV_FILE)))
            + "\",\"offset\":"
            + dv.offset()
            + ",\"sizeInBytes\":"
            + dv.sizeInBytes()
            + ",\"cardinality\":"
            + dv.cardinality()
            + "}";
    StringBuilder version1 = new StringBuilder();
    version1.append(commitInfo(now + 1, "DELETE")).append('\n');
    version1
        .append("{\"remove\":{\"path\":\"")
        .append(DATA_FILE)
        .append("\",\"deletionTimestamp\":")
        .append(now + 1)
        .append(",\"dataChange\":true,\"size\":")
        .append(size)
        .append("}}\n");
    version1.append(addAction(DATA_FILE, size, now + 1, rows.size(), dvJson)).append('\n');
    writeLog(location, 1, version1.toString());
    return location;
  }

  private static long writeParquet(Path file, List<CustomerRow> rows) throws IOException {
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    HadoopFileIO io = new HadoopFileIO(HadoopSupport.configuration());
    OutputFile output = io.newOutputFile(HadoopSupport.uri(file));
    FileAppender<Record> appender =
        Parquet.write(output)
            .schema(IcebergFixtures.SCHEMA)
            .createWriterFunc(GenericParquetWriter::create)
            .overwrite()
            .build();
    try (FileAppender<Record> closer = appender) {
      for (CustomerRow row : rows) {
        closer.add(IcebergFixtures.record(row));
      }
    }
    return Files.size(file);
  }

  private static String protocolAndMetadata(long now) {
    String schema =
        "{\"type\":\"struct\",\"fields\":["
            + "{\"name\":\"id\",\"type\":\"long\",\"nullable\":false,\"metadata\":{}},"
            + "{\"name\":\"name\",\"type\":\"string\",\"nullable\":false,\"metadata\":{}},"
            + "{\"name\":\"city\",\"type\":\"string\",\"nullable\":false,\"metadata\":{}}"
            + "]}";
    StringBuilder sb = new StringBuilder();
    sb.append(commitInfo(now, "CREATE TABLE")).append('\n');
    sb.append(
            "{\"protocol\":{\"minReaderVersion\":3,\"minWriterVersion\":7,"
                + "\"readerFeatures\":[\"deletionVectors\"],"
                + "\"writerFeatures\":[\"deletionVectors\"]}}")
        .append('\n');
    sb.append("{\"metaData\":{");
    sb.append("\"id\":\"").append(UUID.randomUUID()).append("\",");
    sb.append("\"name\":null,\"description\":null,");
    sb.append("\"format\":{\"provider\":\"parquet\",\"options\":{}},");
    sb.append("\"schemaString\":\"").append(jsonEscape(schema)).append("\",");
    sb.append("\"partitionColumns\":[],");
    sb.append("\"configuration\":{\"delta.enableDeletionVectors\":\"true\"},");
    sb.append("\"createdTime\":").append(now);
    sb.append("}}\n");
    return sb.toString();
  }

  private static String addAction(
      String path, long size, long modificationTime, int numRecords, String deletionVector) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"add\":{");
    sb.append("\"path\":\"").append(path).append("\",");
    sb.append("\"partitionValues\":{},");
    sb.append("\"size\":").append(size).append(",");
    sb.append("\"modificationTime\":").append(modificationTime).append(",");
    sb.append("\"dataChange\":true,");
    sb.append("\"stats\":\"{\\\"numRecords\\\":").append(numRecords).append("}\"");
    if (deletionVector != null) {
      sb.append(",\"deletionVector\":").append(deletionVector);
    }
    sb.append("}}");
    return sb.toString();
  }

  private static String commitInfo(long timestamp, String operation) {
    return "{\"commitInfo\":{\"timestamp\":"
        + timestamp
        + ",\"operation\":\""
        + operation
        + "\",\"isBlindAppend\":false}}";
  }

  private static void writeLog(Path table, int version, String body) throws IOException {
    Path log = table.resolve("_delta_log");
    Files.createDirectories(log);
    String name = String.format("%020d.json", version);
    Files.writeString(log.resolve(name), body);
  }

  private static String jsonEscape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
