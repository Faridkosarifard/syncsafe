package io.github.faridkosarifard.syncsafe.model;

/** How a key (or the table as a whole) disagrees between source and target live sets. */
public enum DivergenceKind {
  /**
   * The key is live on the target but not on the source: a deleted source row has come back, or the
   * target never applied the corresponding delete files / deletion vectors.
   */
  RESURRECTED,
  /** The key is live on the source but not on the target. */
  MISSING,
  /** The key is live on both sides but the multiplicity differs. */
  COUNT_MISMATCH;

  public String label() {
    return name().toLowerCase();
  }
}
