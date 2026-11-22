package io.github.faridkosarifard.syncsafe.read;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.faridkosarifard.syncsafe.fixtures.FixtureData;
import io.github.faridkosarifard.syncsafe.fixtures.IcebergFixtures;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.RowKey;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import io.github.faridkosarifard.syncsafe.read.iceberg.IcebergLiveSetReader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IcebergLiveSetReaderTest {
  @TempDir Path tmp;

  @Test
  void appliesPositionDeletes() throws Exception {
    Path table = tmp.resolve("pos");
    IcebergFixtures.createWithPositionDeletes(
        table, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    LiveRowSet live =
        new IcebergLiveSetReader().read(TableRef.parse("iceberg:" + table), FixtureData.KEYS);
    assertThat(live.summary().physicalRowCount()).isEqualTo(10);
    assertThat(live.summary().liveRowCount()).isEqualTo(7);
    assertThat(live.summary().deletedRowCount()).isEqualTo(3);
    assertThat(live.countOf(RowKey.of(List.of(2L)))).isZero();
    assertThat(live.countOf(RowKey.of(List.of(1L)))).isEqualTo(1);
  }

  @Test
  void appliesEqualityDeletes() throws Exception {
    Path table = tmp.resolve("eq");
    IcebergFixtures.createWithEqualityDeletes(
        table, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    LiveRowSet live =
        new IcebergLiveSetReader().read(TableRef.parse("iceberg:" + table), FixtureData.KEYS);
    assertThat(live.summary().liveRowCount()).isEqualTo(7);
    assertThat(live.countOf(RowKey.of(List.of(5L)))).isZero();
    assertThat(live.countOf(RowKey.of(List.of(6L)))).isEqualTo(1);
  }

  @Test
  void dataOnlyTableKeepsEveryRow() throws Exception {
    Path table = tmp.resolve("raw");
    IcebergFixtures.createDataOnly(table, FixtureData.CUSTOMERS);
    LiveRowSet live =
        new IcebergLiveSetReader().read(TableRef.parse("iceberg:" + table), FixtureData.KEYS);
    assertThat(live.summary().liveRowCount()).isEqualTo(10);
    assertThat(live.summary().deletedRowCount()).isZero();
  }
}
