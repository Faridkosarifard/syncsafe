package io.github.faridkosarifard.syncsafe.read.delta;

import io.delta.kernel.Scan;
import io.delta.kernel.Snapshot;
import io.delta.kernel.Table;
import io.delta.kernel.data.ColumnarBatch;
import io.delta.kernel.data.FilteredColumnarBatch;
import io.delta.kernel.data.Row;
import io.delta.kernel.defaults.engine.DefaultEngine;
import io.delta.kernel.engine.Engine;
import io.delta.kernel.engine.FileReadResult;
import io.delta.kernel.internal.InternalScanFileUtils;
import io.delta.kernel.internal.actions.DeletionVectorDescriptor;
import io.delta.kernel.internal.data.ScanStateRow;
import io.delta.kernel.internal.deletionvectors.DeletionVectorStoredBitmap;
import io.delta.kernel.internal.deletionvectors.RoaringBitmapArray;
import io.delta.kernel.internal.util.Utils;
import io.delta.kernel.types.StructType;
import io.delta.kernel.utils.CloseableIterator;
import io.delta.kernel.utils.FileStatus;
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
import java.util.Optional;

/**
 * Computes the live row set of a Delta table by reading data files through Delta Kernel and
 * applying deletion vectors onto file-local row indexes.
 *
 * <p>File statistics ({@code numRecords}) and Iceberg {@code DataFile.recordCount} values emitted
 * by UniForm are physical, pre-deletion-vector counts. This reader applies the vectors and reports
 * both the physical and the live count.
 */
public final class DeltaLiveSetReader implements LiveSetReader {
  @Override
  public LiveRowSet read(TableRef tableRef, List<String> keyColumns) throws IOException {
    Engine engine = DefaultEngine.create(HadoopSupport.configuration());
    Table table = Table.forPath(engine, tableRef.locationUri());
    Snapshot snapshot =
        tableRef.version().isPresent()
            ? table.getSnapshotAsOfVersion(engine, tableRef.version().orElseThrow())
            : table.getLatestSnapshot(engine);

    StructType schema = snapshot.getSchema();
    int[] keyOrds = new int[keyColumns.size()];
    for (int i = 0; i < keyColumns.size(); i++) {
      String key = keyColumns.get(i);
      int ord = schema.indexOf(key);
      if (ord < 0) {
        throw new IllegalArgumentException(
            "key column '" + key + "' is not in the Delta schema of " + tableRef.path());
      }
      keyOrds[i] = ord;
    }

    Scan scan = snapshot.getScanBuilder().build();
    Row scanState = scan.getScanState(engine);
    StructType physicalSchema = ScanStateRow.getPhysicalDataReadSchema(scanState);
    int[] physicalKeyOrds = new int[keyColumns.size()];
    for (int i = 0; i < keyColumns.size(); i++) {
      int ord = physicalSchema.indexOf(keyColumns.get(i));
      if (ord < 0) {
        throw new IllegalArgumentException(
            "key column '"
                + keyColumns.get(i)
                + "' is not materialized in the Delta data files of "
                + tableRef.path());
      }
      physicalKeyOrds[i] = ord;
    }

    Map<RowKey, Long> counts = new LinkedHashMap<>();
    long live = 0L;
    long physical = 0L;
    long deleted = 0L;

    try (CloseableIterator<FilteredColumnarBatch> fileBatches = scan.getScanFiles(engine)) {
      while (fileBatches.hasNext()) {
        FilteredColumnarBatch fileBatch = fileBatches.next();
        try (CloseableIterator<Row> files = fileBatch.getRows()) {
          while (files.hasNext()) {
            Row scanFile = files.next();
            FileCounts fileCounts =
                readScanFile(engine, scanFile, physicalSchema, physicalKeyOrds, counts);
            live += fileCounts.live;
            physical += fileCounts.physical;
            deleted += fileCounts.deleted;
          }
        }
      }
    }

    SideSummary summary =
        new SideSummary(
            TableFormat.DELTA,
            tableRef.locationUri(),
            null,
            snapshot.getVersion(),
            keyColumns,
            live,
            physical,
            deleted,
            null);
    return new LiveRowSet(summary, counts);
  }

  private static FileCounts readScanFile(
      Engine engine,
      Row scanFile,
      StructType physicalSchema,
      int[] keyOrds,
      Map<RowKey, Long> counts)
      throws IOException {
    FileStatus status = InternalScanFileUtils.getAddFileStatus(scanFile);
    RoaringBitmapArray deletedRows = loadBitmap(engine, scanFile);
    long physical = 0L;
    long live = 0L;
    long deleted = 0L;
    long rowIndex = 0L;
    try (CloseableIterator<FileReadResult> physicalIter =
        engine
            .getParquetHandler()
            .readParquetFiles(
                Utils.singletonCloseableIterator(status), physicalSchema, Optional.empty())) {
      while (physicalIter.hasNext()) {
        ColumnarBatch batch = physicalIter.next().getData();
        int size = batch.getSize();
        for (int i = 0; i < size; i++) {
          physical++;
          boolean isDeleted = deletedRows != null && deletedRows.contains(rowIndex);
          if (isDeleted) {
            deleted++;
          } else {
            live++;
            List<Object> values = new ArrayList<>(keyOrds.length);
            for (int ord : keyOrds) {
              values.add(DeltaValueReaders.read(batch.getColumnVector(ord), i));
            }
            counts.merge(RowKey.of(values), 1L, Long::sum);
          }
          rowIndex++;
        }
      }
    }
    return new FileCounts(live, physical, deleted);
  }

  private static RoaringBitmapArray loadBitmap(Engine engine, Row scanFile) throws IOException {
    DeletionVectorDescriptor descriptor =
        InternalScanFileUtils.getDeletionVectorDescriptorFromRow(scanFile);
    if (descriptor == null) {
      return null;
    }
    String tableRoot = InternalScanFileUtils.getAddFileStatus(scanFile).getPath();
    // getDeletionVectorDescriptorFromRow + DeletionVectorStoredBitmap need the table root
    // to resolve relative / UUID storage. Prefer the tableRoot field on the scan file if present.
    Optional<String> tableRootOpt = tableRootFromScanFile(scanFile);
    DeletionVectorStoredBitmap stored = new DeletionVectorStoredBitmap(descriptor, tableRootOpt);
    return stored.load(engine.getFileSystemClient());
  }

  private static Optional<String> tableRootFromScanFile(Row scanFile) {
    try {
      int ordinal = scanFile.getSchema().indexOf("tableRoot");
      if (ordinal >= 0 && !scanFile.isNullAt(ordinal)) {
        String root = scanFile.getString(ordinal);
        if (root != null && !root.isBlank()) {
          return Optional.of(root);
        }
      }
    } catch (RuntimeException ignore) {
      // Older scan-file schemas may omit tableRoot; path-type vectors still resolve.
    }
    return Optional.empty();
  }

  private record FileCounts(long live, long physical, long deleted) {}
}
