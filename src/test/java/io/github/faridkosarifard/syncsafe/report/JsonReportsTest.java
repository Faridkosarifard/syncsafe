package io.github.faridkosarifard.syncsafe.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.faridkosarifard.syncsafe.compare.LiveSetComparator;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.RowKey;
import io.github.faridkosarifard.syncsafe.model.SideSummary;
import io.github.faridkosarifard.syncsafe.model.TableFormat;
import io.github.faridkosarifard.syncsafe.model.Verdict;
import io.github.faridkosarifard.syncsafe.model.VerificationReport;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonReportsTest {
  @TempDir Path tmp;

  @Test
  void roundTripsADivergedReport() throws Exception {
    SideSummary source =
        new SideSummary(TableFormat.DELTA, "file:///src", null, 1L, List.of("id"), 1, 2, 1, null);
    SideSummary target =
        new SideSummary(
            TableFormat.ICEBERG, "file:///tgt", 99L, null, List.of("id"), 2, 2, 0, null);
    LiveRowSet src = new LiveRowSet(source, Map.of(RowKey.of(List.of(1L)), 1L));
    LiveRowSet tgt =
        new LiveRowSet(target, Map.of(RowKey.of(List.of(1L)), 1L, RowKey.of(List.of(2L)), 1L));
    VerificationReport original =
        LiveSetComparator.compare(src, tgt, 25, Instant.parse("2026-11-22T17:00:00Z"));
    Path file = tmp.resolve("report.json");
    JsonReports.write(original, file);
    VerificationReport restored = JsonReports.read(file);
    assertThat(restored.verdict()).isEqualTo(Verdict.DIVERGED);
    assertThat(restored.resurrectedCount()).isEqualTo(1);
    assertThat(restored.source().version()).contains(1L);
    assertThat(restored.target().snapshotId()).contains(99L);
    assertThat(TextReports.toString(restored))
        .contains("verdict: DIVERGED")
        .contains("resurrected");
  }
}
