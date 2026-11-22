package io.github.faridkosarifard.syncsafe.read;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.faridkosarifard.syncsafe.compare.LiveSetComparator;
import io.github.faridkosarifard.syncsafe.fixtures.DeltaFixtures;
import io.github.faridkosarifard.syncsafe.fixtures.FixtureData;
import io.github.faridkosarifard.syncsafe.fixtures.IcebergFixtures;
import io.github.faridkosarifard.syncsafe.model.DivergenceKind;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import io.github.faridkosarifard.syncsafe.model.Verdict;
import io.github.faridkosarifard.syncsafe.model.VerificationReport;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrossFormatVerificationTest {
  @TempDir Path tmp;

  @Test
  void alignedWhenDeletionVectorsAreTranslatedToPositionDeletes() throws Exception {
    Path source = tmp.resolve("aligned-delta");
    Path target = tmp.resolve("aligned-iceberg");
    DeltaFixtures.createWithDeletionVectors(source, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    IcebergFixtures.createWithPositionDeletes(
        target, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    VerificationReport report = compare(source, target);
    assertThat(report.verdict()).isEqualTo(Verdict.ALIGNED);
    assertThat(report.source().liveRowCount()).isEqualTo(7);
    assertThat(report.target().liveRowCount()).isEqualTo(7);
  }

  @Test
  void alignedWhenDeletionVectorsAreTranslatedToEqualityDeletes() throws Exception {
    Path source = tmp.resolve("eq-delta");
    Path target = tmp.resolve("eq-iceberg");
    DeltaFixtures.createWithDeletionVectors(source, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    IcebergFixtures.createWithEqualityDeletes(
        target, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    assertThat(compare(source, target).verdict()).isEqualTo(Verdict.ALIGNED);
  }

  @Test
  void resurrectedWhenTargetOmitsDeleteFiles() throws Exception {
    Path source = tmp.resolve("gap-delta");
    Path target = tmp.resolve("gap-iceberg");
    DeltaFixtures.createWithDeletionVectors(source, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    IcebergFixtures.createDataOnly(target, FixtureData.CUSTOMERS);
    VerificationReport report = compare(source, target);
    assertThat(report.verdict()).isEqualTo(Verdict.DIVERGED);
    assertThat(report.resurrectedCount()).isEqualTo(3);
    assertThat(report.missingCount()).isZero();
    assertThat(report.divergences()).allMatch(d -> d.kind() == DivergenceKind.RESURRECTED);
    assertThat(report.hint()).isPresent();
    assertThat(report.source().physicalRowCount()).isEqualTo(report.target().physicalRowCount());
  }

  @Test
  void missingWhenTargetDeletesExtraRows() throws Exception {
    Path source = tmp.resolve("extra-delta");
    Path target = tmp.resolve("extra-iceberg");
    DeltaFixtures.createWithDeletionVectors(source, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    IcebergFixtures.createWithPositionDeletes(
        target, FixtureData.CUSTOMERS, java.util.Set.of(2L, 5L, 9L, 1L));
    VerificationReport report = compare(source, target);
    assertThat(report.verdict()).isEqualTo(Verdict.DIVERGED);
    assertThat(report.missingCount()).isEqualTo(1);
    assertThat(report.resurrectedCount()).isZero();
  }

  @Test
  void sameFormatIcebergComparisonWorks() throws Exception {
    Path left = tmp.resolve("left");
    Path right = tmp.resolve("right");
    IcebergFixtures.createWithPositionDeletes(left, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    IcebergFixtures.createWithEqualityDeletes(
        right, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    LiveRowSet source =
        TableReaders.readQuietly(TableRef.parse("iceberg:" + left), FixtureData.KEYS);
    LiveRowSet target =
        TableReaders.readQuietly(TableRef.parse("iceberg:" + right), FixtureData.KEYS);
    assertThat(LiveSetComparator.compare(source, target).verdict()).isEqualTo(Verdict.ALIGNED);
  }

  private static VerificationReport compare(Path source, Path target) {
    LiveRowSet src = TableReaders.readQuietly(TableRef.parse("delta:" + source), FixtureData.KEYS);
    LiveRowSet tgt =
        TableReaders.readQuietly(TableRef.parse("iceberg:" + target), FixtureData.KEYS);
    return LiveSetComparator.compare(src, tgt);
  }
}
