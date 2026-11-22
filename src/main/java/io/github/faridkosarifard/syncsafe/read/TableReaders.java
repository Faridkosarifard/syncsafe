package io.github.faridkosarifard.syncsafe.read;

import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.SideSummary;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import io.github.faridkosarifard.syncsafe.read.delta.DeltaLiveSetReader;
import io.github.faridkosarifard.syncsafe.read.iceberg.IcebergLiveSetReader;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dispatches a {@link TableRef} to the Iceberg or Delta live-set reader. */
public final class TableReaders {
  private static final Logger LOG = LoggerFactory.getLogger(TableReaders.class);

  private TableReaders() {}

  public static LiveSetReader forFormat(
      io.github.faridkosarifard.syncsafe.model.TableFormat format) {
    return switch (format) {
      case ICEBERG -> new IcebergLiveSetReader();
      case DELTA -> new DeltaLiveSetReader();
    };
  }

  /**
   * Reads a side and never throws: failures become an incomplete {@link LiveRowSet} so the
   * comparison can still be reported with a non-zero exit code.
   */
  public static LiveRowSet readQuietly(TableRef table, List<String> keyColumns) {
    try {
      return forFormat(table.format()).read(table, keyColumns);
    } catch (Exception e) {
      LOG.warn("failed to read {}: {}", table, e.toString());
      SideSummary summary =
          new SideSummary(
              table.format(),
              table.locationUri(),
              table.snapshotId().orElse(null),
              table.version().orElse(null),
              keyColumns,
              0,
              0,
              0,
              e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      return LiveRowSet.incomplete(summary);
    }
  }
}
