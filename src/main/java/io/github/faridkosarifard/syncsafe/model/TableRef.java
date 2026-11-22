package io.github.faridkosarifard.syncsafe.model;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Identifies one side of a verification: a table location, its format, and an optional snapshot
 * (Iceberg) or version (Delta).
 */
public final class TableRef {
  private final TableFormat format;
  private final Path path;
  private final Long snapshotId;
  private final Long version;

  public TableRef(TableFormat format, Path path, Long snapshotId, Long version) {
    this.format = Objects.requireNonNull(format, "format");
    this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    this.snapshotId = snapshotId;
    this.version = version;
  }

  /**
   * Parses {@code format:path} specifications such as {@code iceberg:/tmp/orders} or {@code
   * delta:/tmp/orders}.
   */
  public static TableRef parse(String spec) {
    return parse(spec, null, null);
  }

  public static TableRef parse(String spec, Long snapshotId, Long version) {
    if (spec == null || spec.isBlank()) {
      throw new IllegalArgumentException("table specification must not be empty");
    }
    int colon = spec.indexOf(':');
    if (colon <= 0) {
      throw new IllegalArgumentException(
          "table specification must be format:path, for example iceberg:/tmp/table: " + spec);
    }
    String formatToken = spec.substring(0, colon);
    String remainder = spec.substring(colon + 1);
    if (remainder.startsWith("file://")) {
      remainder = Path.of(java.net.URI.create(remainder)).toString();
    } else if (remainder.startsWith("file:")) {
      remainder = remainder.substring("file:".length());
    }
    if (remainder.isBlank()) {
      throw new IllegalArgumentException("table path is missing in: " + spec);
    }
    return new TableRef(
        TableFormat.fromString(formatToken), Path.of(remainder), snapshotId, version);
  }

  public TableFormat format() {
    return format;
  }

  public Path path() {
    return path;
  }

  public Optional<Long> snapshotId() {
    return Optional.ofNullable(snapshotId);
  }

  public Optional<Long> version() {
    return Optional.ofNullable(version);
  }

  public TableRef withSnapshotId(Long id) {
    return new TableRef(format, path, id, version);
  }

  public TableRef withVersion(Long ver) {
    return new TableRef(format, path, snapshotId, ver);
  }

  public String locationUri() {
    return path.toUri().toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(format.label()).append(':').append(path);
    snapshotId().ifPresent(id -> sb.append("@snapshot=").append(id));
    version().ifPresent(v -> sb.append("@version=").append(v));
    return sb.toString();
  }
}
