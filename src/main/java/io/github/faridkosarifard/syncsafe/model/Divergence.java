package io.github.faridkosarifard.syncsafe.model;

import java.util.Objects;

/** One sampled key that differs between the source and target live sets. */
public final class Divergence {
  private final DivergenceKind kind;
  private final RowKey key;
  private final long sourceCount;
  private final long targetCount;

  public Divergence(DivergenceKind kind, RowKey key, long sourceCount, long targetCount) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.key = Objects.requireNonNull(key, "key");
    this.sourceCount = sourceCount;
    this.targetCount = targetCount;
  }

  public DivergenceKind kind() {
    return kind;
  }

  public RowKey key() {
    return key;
  }

  public long sourceCount() {
    return sourceCount;
  }

  public long targetCount() {
    return targetCount;
  }
}
