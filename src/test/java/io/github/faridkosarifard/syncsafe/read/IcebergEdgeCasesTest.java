package io.github.faridkosarifard.syncsafe.read;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.faridkosarifard.syncsafe.fixtures.FixtureData;
import io.github.faridkosarifard.syncsafe.fixtures.IcebergFixtures;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IcebergEdgeCasesTest {
  @TempDir Path tmp;

  @Test
  void emptyTableHasZeroLiveRows() {
    Path table = tmp.resolve("empty");
    IcebergFixtures.createEmpty(table);
    LiveRowSet live =
        TableReaders.readQuietly(TableRef.parse("iceberg:" + table), FixtureData.KEYS);
    assertThat(live.incomplete()).isFalse();
    assertThat(live.summary().liveRowCount()).isZero();
    assertThat(live.summary().physicalRowCount()).isZero();
  }

  @Test
  void missingSnapshotAndMissingKeyAreIncomplete() throws Exception {
    Path table = tmp.resolve("snap");
    IcebergFixtures.createDataOnly(table, FixtureData.CUSTOMERS);
    LiveRowSet missingSnap =
        TableReaders.readQuietly(TableRef.parse("iceberg:" + table, 999L, null), FixtureData.KEYS);
    assertThat(missingSnap.incomplete()).isTrue();
    LiveRowSet missingKey =
        TableReaders.readQuietly(TableRef.parse("iceberg:" + table), List.of("nope"));
    assertThat(missingKey.incomplete()).isTrue();
    assertThat(missingKey.error().orElse("")).contains("key column");
  }
}
