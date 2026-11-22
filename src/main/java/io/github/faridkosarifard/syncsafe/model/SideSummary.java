package io.github.faridkosarifard.syncsafe.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Metadata and counts for one side of a verification. */
public final class SideSummary {
  private final TableFormat format;
  private final String location;
  private final Long snapshotId;
  private final Long version;
  private final List<String> keyColumns;
  private final long liveRowCount;
  private final long physicalRowCount;
  private final long deletedRowCount;
  private final String error;

  public SideSummary(
      TableFormat format,
      String location,
      Long snapshotId,
      Long version,
      List<String> keyColumns,
      long liveRowCount,
      long physicalRowCount,
      long deletedRowCount,
      String error) {
    this.format = Objects.requireNonNull(format, "format");
    this.location = Objects.requireNonNull(location, "location");
    this.snapshotId = snapshotId;
    this.version = version;
    this.keyColumns = Collections.unmodifiableList(new ArrayList<>(keyColumns));
    this.liveRowCount = liveRowCount;
    this.physicalRowCount = physicalRowCount;
    this.deletedRowCount = deletedRowCount;
    this.error = error;
  }

  public TableFormat format() {
    return format;
  }

  public String location() {
    return location;
  }

  public Optional<Long> snapshotId() {
    return Optional.ofNullable(snapshotId);
  }

  public Optional<Long> version() {
    return Optional.ofNullable(version);
  }

  public List<String> keyColumns() {
    return keyColumns;
  }

  public long liveRowCount() {
    return liveRowCount;
  }

  public long physicalRowCount() {
    return physicalRowCount;
  }

  public long deletedRowCount() {
    return deletedRowCount;
  }

  public Optional<String> error() {
    return Optional.ofNullable(error);
  }

  public boolean incomplete() {
    return error != null;
  }
}
