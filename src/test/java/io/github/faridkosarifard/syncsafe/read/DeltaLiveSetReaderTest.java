package io.github.faridkosarifard.syncsafe.read;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.faridkosarifard.syncsafe.fixtures.DeltaFixtures;
import io.github.faridkosarifard.syncsafe.fixtures.FixtureData;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.RowKey;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import io.github.faridkosarifard.syncsafe.read.delta.DeltaLiveSetReader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeltaLiveSetReaderTest {
  @TempDir Path tmp;

  @Test
  void appliesDeletionVectorsAndKeepsPhysicalCount() throws Exception {
    Path table = tmp.resolve("dv");
    DeltaFixtures.createWithDeletionVectors(table, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    LiveRowSet live =
        new DeltaLiveSetReader().read(TableRef.parse("delta:" + table), FixtureData.KEYS);
    assertThat(live.summary().version()).contains(1L);
    assertThat(live.summary().physicalRowCount()).isEqualTo(10);
    assertThat(live.summary().liveRowCount()).isEqualTo(7);
    assertThat(live.summary().deletedRowCount()).isEqualTo(3);
    assertThat(live.countOf(RowKey.of(List.of(2L)))).isZero();
    assertThat(live.countOf(RowKey.of(List.of(4L)))).isEqualTo(1);
  }

  @Test
  void versionZeroIsThePreDeleteSnapshot() throws Exception {
    Path table = tmp.resolve("versions");
    DeltaFixtures.createWithDeletionVectors(table, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    LiveRowSet atCreate =
        new DeltaLiveSetReader().read(TableRef.parse("delta:" + table, null, 0L), FixtureData.KEYS);
    assertThat(atCreate.summary().liveRowCount()).isEqualTo(10);
    assertThat(atCreate.summary().deletedRowCount()).isZero();
  }

  @Test
  void dataOnlyTableHasNoDeletes() throws Exception {
    Path table = tmp.resolve("raw");
    DeltaFixtures.createDataOnly(table, FixtureData.CUSTOMERS);
    LiveRowSet live =
        new DeltaLiveSetReader().read(TableRef.parse("delta:" + table), FixtureData.KEYS);
    assertThat(live.summary().liveRowCount()).isEqualTo(10);
    assertThat(live.summary().deletedRowCount()).isZero();
  }
}
