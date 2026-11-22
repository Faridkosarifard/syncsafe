package io.github.faridkosarifard.syncsafe.model;

import io.github.faridkosarifard.syncsafe.SyncsafeVersion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Machine-readable result of comparing two live row sets. Schema version 1.0. */
public final class VerificationReport {
  public static final String SCHEMA_VERSION = "1.0";

  private final String schemaVersion;
  private final String tool;
  private final String toolVersion;
  private final Instant generatedAt;
  private final List<String> keyColumns;
  private final SideSummary source;
  private final SideSummary target;
  private final Verdict verdict;
  private final long resurrectedCount;
  private final long missingCount;
  private final long countMismatchCount;
  private final List<Divergence> divergences;
  private final int sampleLimit;
  private final String hint;

  public VerificationReport(
      Instant generatedAt,
      List<String> keyColumns,
      SideSummary source,
      SideSummary target,
      Verdict verdict,
      long resurrectedCount,
      long missingCount,
      long countMismatchCount,
      List<Divergence> divergences,
      int sampleLimit,
      String hint) {
    this.schemaVersion = SCHEMA_VERSION;
    this.tool = "syncsafe";
    this.toolVersion = SyncsafeVersion.VERSION;
    this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
    this.keyColumns = Collections.unmodifiableList(new ArrayList<>(keyColumns));
    this.source = Objects.requireNonNull(source, "source");
    this.target = Objects.requireNonNull(target, "target");
    this.verdict = Objects.requireNonNull(verdict, "verdict");
    this.resurrectedCount = resurrectedCount;
    this.missingCount = missingCount;
    this.countMismatchCount = countMismatchCount;
    this.divergences = Collections.unmodifiableList(new ArrayList<>(divergences));
    this.sampleLimit = sampleLimit;
    this.hint = hint;
  }

  public String schemaVersion() {
    return schemaVersion;
  }

  public String tool() {
    return tool;
  }

  public String toolVersion() {
    return toolVersion;
  }

  public Instant generatedAt() {
    return generatedAt;
  }

  public List<String> keyColumns() {
    return keyColumns;
  }

  public SideSummary source() {
    return source;
  }

  public SideSummary target() {
    return target;
  }

  public Verdict verdict() {
    return verdict;
  }

  public long resurrectedCount() {
    return resurrectedCount;
  }

  public long missingCount() {
    return missingCount;
  }

  public long countMismatchCount() {
    return countMismatchCount;
  }

  public long totalDivergences() {
    return resurrectedCount + missingCount + countMismatchCount;
  }

  public List<Divergence> divergences() {
    return divergences;
  }

  public int sampleLimit() {
    return sampleLimit;
  }

  public Optional<String> hint() {
    return Optional.ofNullable(hint);
  }

  public Map<String, Long> summaryCounts() {
    Map<String, Long> counts = new LinkedHashMap<>();
    counts.put(DivergenceKind.RESURRECTED.label(), resurrectedCount);
    counts.put(DivergenceKind.MISSING.label(), missingCount);
    counts.put(DivergenceKind.COUNT_MISMATCH.label(), countMismatchCount);
    return counts;
  }
}
