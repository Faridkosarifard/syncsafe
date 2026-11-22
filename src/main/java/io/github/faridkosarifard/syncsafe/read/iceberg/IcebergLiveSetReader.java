package io.github.faridkosarifard.syncsafe.read.iceberg;

import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.RowKey;
import io.github.faridkosarifard.syncsafe.model.SideSummary;
import io.github.faridkosarifard.syncsafe.model.TableFormat;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import io.github.faridkosarifard.syncsafe.read.HadoopSupport;
import io.github.faridkosarifard.syncsafe.read.LiveSetReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.io.CloseableIterable;

/**
 * Computes the live row set of an Iceberg table by scanning the chosen snapshot through {@link
 * IcebergGenerics}, which applies position deletes and equality deletes.
 *
 * <p>{@code DataFile.recordCount()} is the physical (pre-delete) count. UniForm documents the same
 * distinction for Iceberg metadata it emits alongside Delta deletion vectors: tooling that treats
 * {@code recordCount} as a logical count will be wrong. This reader always applies deletes and
 * reports both numbers.
 */
public final class IcebergLiveSetReader implements LiveSetReader {
  @Override
  public LiveRowSet read(TableRef tableRef, List<String> keyColumns) throws IOException {
    HadoopTables tables = new HadoopTables(HadoopSupport.configuration());
    Table table = tables.load(tableRef.locationUri());
    Snapshot snapshot =
        tableRef
            .snapshotId()
            .map(
                id -> {
                  Snapshot found = table.snapshot(id);
                  if (found == null) {
                    throw new IllegalArgumentException(
                        "Iceberg snapshot " + id + " does not exist at " + tableRef.path());
                  }
                  return found;
                })
            .orElseGet(table::currentSnapshot);
    if (snapshot == null) {
      SideSummary summary =
          new SideSummary(
              TableFormat.ICEBERG, tableRef.locationUri(), null, null, keyColumns, 0, 0, 0, null);
      return new LiveRowSet(summary, Map.of());
    }

    Schema schema = table.schema();
    for (String key : keyColumns) {
      if (schema.findField(key) == null) {
        throw new IllegalArgumentException(
            "key column '" + key + "' is not in the Iceberg schema of " + tableRef.path());
      }
    }

    long physical = 0L;
    TableScan scan = table.newScan().useSnapshot(snapshot.snapshotId());
    try (CloseableIterable<FileScanTask> tasks = scan.planFiles()) {
      for (FileScanTask task : tasks) {
        physical += task.file().recordCount();
      }
    }

    Map<RowKey, Long> counts = new LinkedHashMap<>();
    long live = 0L;
    IcebergGenerics.ScanBuilder builder =
        IcebergGenerics.read(table).useSnapshot(snapshot.snapshotId());
    try (CloseableIterable<Record> records = builder.build()) {
      for (Record record : records) {
        live++;
        List<Object> values = new ArrayList<>(keyColumns.size());
        for (String key : keyColumns) {
          values.add(record.getField(key));
        }
        RowKey rowKey = RowKey.of(values);
        counts.merge(rowKey, 1L, Long::sum);
      }
    }

    long deleted = Math.max(0L, physical - live);
    SideSummary summary =
        new SideSummary(
            TableFormat.ICEBERG,
            tableRef.locationUri(),
            snapshot.snapshotId(),
            null,
            keyColumns,
            live,
            physical,
            deleted,
            null);
    return new LiveRowSet(summary, counts);
  }
}
