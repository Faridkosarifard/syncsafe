package io.github.faridkosarifard.syncsafe.fixtures;

import io.github.faridkosarifard.syncsafe.read.HadoopSupport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.deletes.EqualityDeleteWriter;
import org.apache.iceberg.deletes.PositionDelete;
import org.apache.iceberg.deletes.PositionDeleteWriter;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.types.Types;

/** Builds local Iceberg tables used as conversion targets in tests and the demo. */
public final class IcebergFixtures {
  public static final Schema SCHEMA =
      new Schema(
          Types.NestedField.required(1, "id", Types.LongType.get()),
          Types.NestedField.required(2, "name", Types.StringType.get()),
          Types.NestedField.required(3, "city", Types.StringType.get()));

  private IcebergFixtures() {}

  public static Table createEmpty(Path location) {
    HadoopTables tables = new HadoopTables(HadoopSupport.configuration());
    return tables.create(
        SCHEMA,
        PartitionSpec.unpartitioned(),
        SortOrder.unsorted(),
        Map.of("format-version", "2", "write.delete.mode", "merge-on-read"),
        HadoopSupport.uri(location));
  }

  /**
   * Writes every customer into a single data file and does not add delete files. This is what a
   * metadata converter produces when it copies data-file pointers and drops deletion vectors.
   */
  public static Table createDataOnly(Path location, List<CustomerRow> rows) throws IOException {
    Table table = createEmpty(location);
    DataFile dataFile = writeDataFile(table, "data/all.parquet", rows);
    table.newAppend().appendFile(dataFile).commit();
    return table;
  }

  /** Same data file as {@link #createDataOnly}, plus Iceberg v2 position deletes for the ids. */
  public static Table createWithPositionDeletes(
      Path location, List<CustomerRow> rows, Set<Long> deletedIds) throws IOException {
    Table table = createEmpty(location);
    DataFile dataFile = writeDataFile(table, "data/all.parquet", rows);
    table.newAppend().appendFile(dataFile).commit();
    DeleteFile deletes = writePositionDeletes(table, dataFile, rows, deletedIds);
    table.newRowDelta().addDeletes(deletes).commit();
    return table;
  }

  /** Same data file as {@link #createDataOnly}, plus equality deletes on the {@code id} field. */
  public static Table createWithEqualityDeletes(
      Path location, List<CustomerRow> rows, Set<Long> deletedIds) throws IOException {
    Table table = createEmpty(location);
    DataFile dataFile = writeDataFile(table, "data/all.parquet", rows);
    table.newAppend().appendFile(dataFile).commit();
    DeleteFile deletes = writeEqualityDeletes(table, rows, deletedIds);
    table.newRowDelta().addDeletes(deletes).commit();
    return table;
  }

  public static DataFile writeDataFile(Table table, String relative, List<CustomerRow> rows)
      throws IOException {
    OutputFile output = table.io().newOutputFile(table.location() + "/" + relative);
    DataWriter<Record> writer =
        Parquet.writeData(output)
            .schema(SCHEMA)
            .createWriterFunc(GenericParquetWriter::create)
            .overwrite()
            .withSpec(PartitionSpec.unpartitioned())
            .build();
    try (DataWriter<Record> closer = writer) {
      for (CustomerRow row : rows) {
        closer.write(record(row));
      }
    }
    return writer.toDataFile();
  }

  private static DeleteFile writePositionDeletes(
      Table table, DataFile dataFile, List<CustomerRow> rows, Set<Long> deletedIds)
      throws IOException {
    OutputFile output = table.io().newOutputFile(table.location() + "/data/deletes-pos.parquet");
    PositionDeleteWriter<Record> writer =
        Parquet.writeDeletes(output)
            .createWriterFunc(GenericParquetWriter::create)
            .overwrite()
            .withSpec(PartitionSpec.unpartitioned())
            .buildPositionWriter();
    try (PositionDeleteWriter<Record> closer = writer) {
      PositionDelete<Record> pos = PositionDelete.create();
      for (int i = 0; i < rows.size(); i++) {
        if (deletedIds.contains(rows.get(i).id())) {
          closer.write(pos.set(dataFile.path().toString(), i));
        }
      }
    }
    return writer.toDeleteFile();
  }

  private static DeleteFile writeEqualityDeletes(
      Table table, List<CustomerRow> rows, Set<Long> deletedIds) throws IOException {
    OutputFile output = table.io().newOutputFile(table.location() + "/data/deletes-eq.parquet");
    EqualityDeleteWriter<Record> writer =
        Parquet.writeDeletes(output)
            .createWriterFunc(GenericParquetWriter::create)
            .overwrite()
            .rowSchema(SCHEMA)
            .equalityFieldIds(1)
            .withSpec(PartitionSpec.unpartitioned())
            .buildEqualityWriter();
    try (EqualityDeleteWriter<Record> closer = writer) {
      for (CustomerRow row : rows) {
        if (deletedIds.contains(row.id())) {
          closer.write(record(row));
        }
      }
    }
    return writer.toDeleteFile();
  }

  public static Record record(CustomerRow row) {
    GenericRecord record = GenericRecord.create(SCHEMA);
    record.setField("id", row.id());
    record.setField("name", row.name());
    record.setField("city", row.city());
    return record;
  }
}
