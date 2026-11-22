package io.github.faridkosarifard.syncsafe.compare;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.faridkosarifard.syncsafe.model.DivergenceKind;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.RowKey;
import io.github.faridkosarifard.syncsafe.model.SideSummary;
import io.github.faridkosarifard.syncsafe.model.TableFormat;
import io.github.faridkosarifard.syncsafe.model.Verdict;
import io.github.faridkosarifard.syncsafe.model.VerificationReport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveSetComparatorTest {
  private static final List<String> KEYS = List.of("id");
  private static final Instant NOW = Instant.parse("2026-11-22T17:00:00Z");

  @Test
  void alignedWhenKeysAndCountsMatch() {
    LiveRowSet source = set(TableFormat.DELTA, 7, 10, 3, Map.of(key(1), 1L, key(3), 1L));
    LiveRowSet target = set(TableFormat.ICEBERG, 7, 10, 3, Map.of(key(1), 1L, key(3), 1L));
    VerificationReport report = LiveSetComparator.compare(source, target, 25, NOW);
    assertThat(report.verdict()).isEqualTo(Verdict.ALIGNED);
    assertThat(report.totalDivergences()).isZero();
    assertThat(report.hint()).isEmpty();
  }

  @Test
  void resurrectedWhenTargetStillHoldsADeletedKey() {
    LiveRowSet source = set(TableFormat.DELTA, 1, 2, 1, Map.of(key(1), 1L));
    LiveRowSet target = set(TableFormat.ICEBERG, 2, 2, 0, Map.of(key(1), 1L, key(2), 1L));
    VerificationReport report = LiveSetComparator.compare(source, target, 25, NOW);
    assertThat(report.verdict()).isEqualTo(Verdict.DIVERGED);
    assertThat(report.resurrectedCount()).isEqualTo(1);
    assertThat(report.divergences()).hasSize(1);
    assertThat(report.divergences().get(0).kind()).isEqualTo(DivergenceKind.RESURRECTED);
    assertThat(report.hint()).isPresent();
    assertThat(report.hint().get()).contains("Physical row counts match");
  }

  @Test
  void missingWhenTargetDroppedALiveKey() {
    LiveRowSet source = set(TableFormat.ICEBERG, 2, 2, 0, Map.of(key(1), 1L, key(2), 1L));
    LiveRowSet target = set(TableFormat.DELTA, 1, 2, 1, Map.of(key(1), 1L));
    VerificationReport report = LiveSetComparator.compare(source, target, 25, NOW);
    assertThat(report.missingCount()).isEqualTo(1);
    assertThat(report.divergences().get(0).kind()).isEqualTo(DivergenceKind.MISSING);
  }

  @Test
  void countMismatchWhenMultiplicityDiffers() {
    LiveRowSet source = set(TableFormat.DELTA, 2, 2, 0, Map.of(key(1), 2L));
    LiveRowSet target = set(TableFormat.ICEBERG, 1, 1, 0, Map.of(key(1), 1L));
    VerificationReport report = LiveSetComparator.compare(source, target, 25, NOW);
    assertThat(report.countMismatchCount()).isEqualTo(1);
    assertThat(report.divergences().get(0).sourceCount()).isEqualTo(2);
    assertThat(report.divergences().get(0).targetCount()).isEqualTo(1);
  }

  @Test
  void incompleteWhenASideFailedToRead() {
    SideSummary failed =
        new SideSummary(
            TableFormat.DELTA, "file:///missing", null, null, KEYS, 0, 0, 0, "not found");
    LiveRowSet source = LiveRowSet.incomplete(failed);
    LiveRowSet target = set(TableFormat.ICEBERG, 0, 0, 0, Map.of());
    VerificationReport report = LiveSetComparator.compare(source, target, 25, NOW);
    assertThat(report.verdict()).isEqualTo(Verdict.INCOMPLETE);
    assertThat(report.hint()).contains("source: not found");
  }

  @Test
  void sampleLimitCapsReportedKeys() {
    Map<RowKey, Long> onlyTarget = Map.of(key(1), 1L, key(2), 1L, key(3), 1L);
    LiveRowSet source = set(TableFormat.DELTA, 0, 3, 3, Map.of());
    LiveRowSet target = set(TableFormat.ICEBERG, 3, 3, 0, onlyTarget);
    VerificationReport report = LiveSetComparator.compare(source, target, 2, NOW);
    assertThat(report.resurrectedCount()).isEqualTo(3);
    assertThat(report.divergences()).hasSize(2);
  }

  private static RowKey key(long id) {
    return RowKey.of(List.of(id));
  }

  private static LiveRowSet set(
      TableFormat format, long live, long physical, long deleted, Map<RowKey, Long> counts) {
    SideSummary summary =
        new SideSummary(
            format, "file:///tmp/" + format.label(), null, 0L, KEYS, live, physical, deleted, null);
    return new LiveRowSet(summary, counts);
  }
}
