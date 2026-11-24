package io.github.faridkosarifard.syncsafe.read;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.faridkosarifard.syncsafe.compare.LiveSetComparator;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import io.github.faridkosarifard.syncsafe.model.Verdict;
import io.github.faridkosarifard.syncsafe.model.VerificationReport;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.hadoop.HadoopFileIO;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The same rows written as Delta and as Iceberg must align when keyed on non-numeric types. */
class CrossFormatKeyTypesTest {
  @TempDir Path tmp;

  @Test
  void dateKeysAlign() throws Exception {
    assertAligned(
        Types.DateType.get(),
        "date",
        List.of(LocalDate.of(2026, 1, 1), LocalDate.of(1969, 12, 31), LocalDate.of(2026, 11, 24)));
  }

  @Test
  void timestampWithZoneKeysAlign() throws Exception {
    assertAligned(
        Types.TimestampType.withZone(),
        "timestamp",
        List.of(
            OffsetDateTime.of(2026, 1, 1, 12, 30, 0, 123_456_000, ZoneOffset.UTC),
            OffsetDateTime.of(1969, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2026, 11, 24, 0, 0, 0, 0, ZoneOffset.UTC)));
  }

  @Test
  void timestampWithoutZoneKeysAlign() throws Exception {
    assertAligned(
        Types.TimestampType.withoutZone(),
        "timestamp_ntz",
        List.of(
            LocalDateTime.of(2026, 1, 1, 12, 30, 0, 123_456_000),
            LocalDateTime.of(1969, 12, 31, 23, 59, 59),
            LocalDateTime.of(2026, 11, 24, 0, 0)));
  }

  @Test
  void binaryKeysAlign() throws Exception {
    assertAligned(
        Types.BinaryType.get(),
        "binary",
        List.of(
            ByteBuffer.wrap(new byte[] {0x00, 0x01}),
            ByteBuffer.wrap(new byte[] {(byte) 0xff}),
            ByteBuffer.wrap(new byte[] {0x7f, 0x10, 0x20})));
  }

  private void assertAligned(Type icebergType, String deltaType, List<Object> keys)
      throws IOException {
    Schema schema =
        new Schema(
            Types.NestedField.required(1, "id", Types.LongType.get()),
            Types.NestedField.required(2, "k", icebergType));
    List<Record> rows = new ArrayList<>();
    for (int i = 0; i < keys.size(); i++) {
      GenericRecord row = GenericRecord.create(schema);
      row.setField("id", (long) i);
      row.setField("k", keys.get(i));
      rows.add(row);
    }
    Path iceberg = tmp.resolve("iceberg");
    Path delta = tmp.resolve("delta");
    writeIceberg(iceberg, schema, rows);
    writeDelta(delta, schema, deltaType, rows);

    LiveRowSet source = TableReaders.readQuietly(TableRef.parse("delta:" + delta), List.of("k"));
    LiveRowSet target =
        TableReaders.readQuietly(TableRef.parse("iceberg:" + iceberg), List.of("k"));
    VerificationReport report = LiveSetComparator.compare(source, target);

    assertThat(source.error()).isEmpty();
    assertThat(target.error()).isEmpty();
    assertThat(source.counts()).hasSize(keys.size());
    assertThat(report.verdict()).isEqualTo(Verdict.ALIGNED);
  }

  private static void writeIceberg(Path location, Schema schema, List<Record> rows)
      throws IOException {
    Table table =
        new HadoopTables(HadoopSupport.configuration())
            .create(
                schema,
                PartitionSpec.unpartitioned(),
                Map.of("format-version", "2"),
                HadoopSupport.uri(location));
    DataWriter<Record> writer =
        Parquet.writeData(
                table.io().newOutputFile(table.locationProvider().newDataLocation("a.parquet")))
            .schema(schema)
            .createWriterFunc(GenericParquetWriter::create)
            .withSpec(PartitionSpec.unpartitioned())
            .overwrite()
            .build();
    try (DataWriter<Record> closer = writer) {
      rows.forEach(closer::write);
    }
    table.newAppend().appendFile(writer.toDataFile()).commit();
  }

  private static void writeDelta(Path location, Schema schema, String keyType, List<Record> rows)
      throws IOException {
    Files.createDirectories(location.resolve("_delta_log"));
    Path dataFile = location.resolve("part-00000.parquet");
    FileAppender<Record> appender =
        Parquet.write(
                new HadoopFileIO(HadoopSupport.configuration())
                    .newOutputFile(HadoopSupport.uri(dataFile)))
            .schema(schema)
            .createWriterFunc(GenericParquetWriter::create)
            .overwrite()
            .build();
    try (FileAppender<Record> closer = appender) {
      rows.forEach(closer::add);
    }
    String deltaSchema =
        "{\"type\":\"struct\",\"fields\":["
            + "{\"name\":\"id\",\"type\":\"long\",\"nullable\":false,\"metadata\":{}},"
            + "{\"name\":\"k\",\"type\":\""
            + keyType
            + "\",\"nullable\":false,\"metadata\":{}}]}";
    String log =
        "{\"protocol\":{\"minReaderVersion\":3,\"minWriterVersion\":7,"
            + "\"readerFeatures\":[\"timestampNtz\"],\"writerFeatures\":[\"timestampNtz\"]}}\n"
            + "{\"metaData\":{\"id\":\""
            + UUID.randomUUID()
            + "\",\"format\":{\"provider\":\"parquet\",\"options\":{}},\"schemaString\":\""
            + deltaSchema.replace("\"", "\\\"")
            + "\",\"partitionColumns\":[],\"configuration\":{},\"createdTime\":0}}\n"
            + "{\"add\":{\"path\":\"part-00000.parquet\",\"partitionValues\":{},\"size\":"
            + Files.size(dataFile)
            + ",\"modificationTime\":0,\"dataChange\":true}}\n";
    Files.writeString(location.resolve("_delta_log").resolve(String.format("%020d.json", 0)), log);
  }
}
