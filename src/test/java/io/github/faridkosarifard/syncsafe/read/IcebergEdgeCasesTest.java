package io.github.faridkosarifard.syncsafe.read;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.faridkosarifard.syncsafe.fixtures.FixtureData;
import io.github.faridkosarifard.syncsafe.fixtures.IcebergFixtures;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.TableFormat;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IcebergEdgeCasesTest {
  @TempDir Path tmp;

  @Test
  void emptyTableHasZeroLiveRows() {
    Path table = tmp.resolve("empty");
    IcebergFixtures.createEmpty(table);
    LiveRowSet live =
        TableReaders.readQuietly(TableRef.parse("iceberg:" + table), FixtureData.KEYS);
    assertThat(live.incomplete()).isFalse();
    assertThat(live.summary().liveRowCount()).isZero();
    assertThat(live.summary().physicalRowCount()).isZero();
  }

  @Test
  void missingSnapshotAndMissingKeyAreIncomplete() throws Exception {
    Path table = tmp.resolve("snap");
    IcebergFixtures.createDataOnly(table, FixtureData.CUSTOMERS);
    LiveRowSet missingSnap =
        TableReaders.readQuietly(TableRef.parse("iceberg:" + table, 999L, null), FixtureData.KEYS);
    assertThat(missingSnap.incomplete()).isTrue();
    LiveRowSet missingKey =
        TableReaders.readQuietly(TableRef.parse("iceberg:" + table), List.of("nope"));
    assertThat(missingKey.incomplete()).isTrue();
    assertThat(missingKey.error().orElse("")).contains("key column");
  }

  @Test
  void nestedKeyColumnIsRejected() throws Exception {
    Path table = tmp.resolve("nested");
    Schema schema =
        new Schema(
            Types.NestedField.required(1, "id", Types.LongType.get()),
            Types.NestedField.optional(
                2,
                "s",
                Types.StructType.of(Types.NestedField.required(3, "k", Types.LongType.get()))));
    Table created =
        new HadoopTables(HadoopSupport.configuration())
            .create(
                schema,
                PartitionSpec.unpartitioned(),
                Map.of("format-version", "2"),
                HadoopSupport.uri(table));
    DataWriter<Record> writer =
        Parquet.writeData(
                created.io().newOutputFile(created.locationProvider().newDataLocation("a.parquet")))
            .schema(schema)
            .createWriterFunc(GenericParquetWriter::create)
            .withSpec(PartitionSpec.unpartitioned())
            .overwrite()
            .build();
    try (DataWriter<Record> closer = writer) {
      GenericRecord inner = GenericRecord.create(schema.findField("s").type().asStructType());
      inner.setField("k", 7L);
      GenericRecord row = GenericRecord.create(schema);
      row.setField("id", 1L);
      row.setField("s", inner);
      closer.write(row);
    }
    DataFile dataFile = writer.toDataFile();
    created.newAppend().appendFile(dataFile).commit();

    LiveRowSet live = TableReaders.readQuietly(TableRef.parse("iceberg:" + table), List.of("s.k"));
    assertThat(live.incomplete()).isTrue();
    assertThat(live.error().orElse("")).contains("nested field");
  }

  @Test
  void pinnedSnapshotUsesItsOwnSchemaAfterRename() throws Exception {
    Path location = tmp.resolve("renamed");
    Table table =
        IcebergFixtures.createWithPositionDeletes(
            location, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    long snapshotId = table.currentSnapshot().snapshotId();
    table.updateSchema().renameColumn("id", "customer_id").commit();
    TableRef pinned = new TableRef(TableFormat.ICEBERG, location, snapshotId, null);

    LiveRowSet newName = TableReaders.readQuietly(pinned, List.of("customer_id"));
    assertThat(newName.incomplete()).isTrue();
    assertThat(newName.error().orElse("")).contains("snapshot " + snapshotId);

    LiveRowSet oldName = TableReaders.readQuietly(pinned, List.of("id"));
    assertThat(oldName.incomplete()).isFalse();
    assertThat(oldName.summary().liveRowCount()).isEqualTo(7);
    assertThat(oldName.counts().keySet()).noneMatch(key -> key.values().contains(null));
  }
}
