package io.github.faridkosarifard.syncsafe.model;

/** Lakehouse table formats Syncsafe can read in this release. */
public enum TableFormat {
  ICEBERG,
  DELTA;

  public static TableFormat fromString(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("table format must not be null");
    }
    String normalized = raw.trim().toLowerCase();
    if ("iceberg".equals(normalized) || "apache-iceberg".equals(normalized)) {
      return ICEBERG;
    }
    if ("delta".equals(normalized)
        || "delta-lake".equals(normalized)
        || "deltalake".equals(normalized)) {
      return DELTA;
    }
    throw new IllegalArgumentException(
        "unsupported table format: " + raw + " (expected iceberg or delta)");
  }

  public String label() {
    return name().toLowerCase();
  }
}
