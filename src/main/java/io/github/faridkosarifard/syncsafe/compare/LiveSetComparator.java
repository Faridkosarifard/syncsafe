package io.github.faridkosarifard.syncsafe.compare;

import io.github.faridkosarifard.syncsafe.model.Divergence;
import io.github.faridkosarifard.syncsafe.model.DivergenceKind;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.RowKey;
import io.github.faridkosarifard.syncsafe.model.Verdict;
import io.github.faridkosarifard.syncsafe.model.VerificationReport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Compares two live row sets and builds a {@link VerificationReport}. */
public final class LiveSetComparator {
  public static final int DEFAULT_SAMPLE_LIMIT = 25;

  private LiveSetComparator() {}

  public static VerificationReport compare(LiveRowSet source, LiveRowSet target) {
    return compare(source, target, DEFAULT_SAMPLE_LIMIT, Instant.now());
  }

  public static VerificationReport compare(LiveRowSet source, LiveRowSet target, int sampleLimit) {
    return compare(source, target, sampleLimit, Instant.now());
  }

  public static VerificationReport compare(
      LiveRowSet source, LiveRowSet target, int sampleLimit, Instant generatedAt) {
    if (source.incomplete() || target.incomplete()) {
      String hint = buildIncompleteHint(source, target);
      return new VerificationReport(
          generatedAt,
          source.keyColumns().isEmpty() ? target.keyColumns() : source.keyColumns(),
          source.summary(),
          target.summary(),
          Verdict.INCOMPLETE,
          0,
          0,
          0,
          List.of(),
          sampleLimit,
          hint);
    }

    Set<RowKey> allKeys = new LinkedHashSet<>();
    allKeys.addAll(source.counts().keySet());
    allKeys.addAll(target.counts().keySet());

    long resurrected = 0;
    long missing = 0;
    long mismatches = 0;
    List<Divergence> samples = new ArrayList<>();

    for (RowKey key : allKeys) {
      long src = source.countOf(key);
      long tgt = target.countOf(key);
      if (src == tgt) {
        continue;
      }
      DivergenceKind kind;
      if (src == 0 && tgt > 0) {
        kind = DivergenceKind.RESURRECTED;
        resurrected++;
      } else if (src > 0 && tgt == 0) {
        kind = DivergenceKind.MISSING;
        missing++;
      } else {
        kind = DivergenceKind.COUNT_MISMATCH;
        mismatches++;
      }
      if (samples.size() < sampleLimit) {
        samples.add(new Divergence(kind, key, src, tgt));
      }
    }

    Verdict verdict =
        (resurrected + missing + mismatches) == 0 ? Verdict.ALIGNED : Verdict.DIVERGED;
    String hint = null;
    if (verdict == Verdict.DIVERGED
        && source.summary().physicalRowCount() == target.summary().physicalRowCount()
        && source.summary().liveRowCount() != target.summary().liveRowCount()
        && source.summary().physicalRowCount() > 0) {
      hint =
          "Physical row counts match ("
              + source.summary().physicalRowCount()
              + ") but live counts differ. The target may be missing delete files or deletion"
              + " vectors, so it is exposing rows the source has already deleted.";
    }

    return new VerificationReport(
        generatedAt,
        source.keyColumns(),
        source.summary(),
        target.summary(),
        verdict,
        resurrected,
        missing,
        mismatches,
        samples,
        sampleLimit,
        hint);
  }

  private static String buildIncompleteHint(LiveRowSet source, LiveRowSet target) {
    List<String> parts = new ArrayList<>();
    source.error().ifPresent(err -> parts.add("source: " + err));
    target.error().ifPresent(err -> parts.add("target: " + err));
    return parts.isEmpty()
        ? "one or both sides could not be read completely"
        : String.join("; ", parts);
  }
}
