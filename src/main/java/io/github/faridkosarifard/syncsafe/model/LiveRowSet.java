package io.github.faridkosarifard.syncsafe.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The live (post-delete) key multiset of one table snapshot or version. */
public final class LiveRowSet {
  private final SideSummary summary;
  private final Map<RowKey, Long> counts;

  public LiveRowSet(SideSummary summary, Map<RowKey, Long> counts) {
    this.summary = Objects.requireNonNull(summary, "summary");
    this.counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
  }

  public static LiveRowSet incomplete(SideSummary summary) {
    return new LiveRowSet(summary, Map.of());
  }

  public SideSummary summary() {
    return summary;
  }

  public Map<RowKey, Long> counts() {
    return counts;
  }

  public long countOf(RowKey key) {
    return counts.getOrDefault(key, 0L);
  }

  public Optional<String> error() {
    return summary.error();
  }

  public boolean incomplete() {
    return summary.incomplete();
  }

  public List<String> keyColumns() {
    return summary.keyColumns();
  }
}
