package io.github.faridkosarifard.syncsafe.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RowKeyTest {
  @Test
  void canonicalizesIntegralTypesAndCharSequences() {
    RowKey left = RowKey.of(List.of((short) 7, new StringBuilder("Ada")));
    RowKey right = RowKey.of(List.of(7, "Ada"));
    assertThat(left).isEqualTo(right);
    assertThat(left.hashCode()).isEqualTo(right.hashCode());
    assertThat(left.render()).isEqualTo("[7, \"Ada\"]");
  }

  @Test
  void treatsNullsAsDistinctValues() {
    RowKey withNull = RowKey.of(Arrays.asList(1L, null));
    RowKey without = RowKey.of(List.of(1L, "x"));
    assertThat(withNull).isNotEqualTo(without);
    assertThat(withNull.render()).isEqualTo("[1, null]");
  }

  @Test
  void tableRefParseAcceptsFormatAndPath() {
    TableRef ref = TableRef.parse("iceberg:/tmp/orders", 42L, null);
    assertThat(ref.format()).isEqualTo(TableFormat.ICEBERG);
    assertThat(ref.path().toString()).isEqualTo("/tmp/orders");
    assertThat(ref.snapshotId()).contains(42L);
    assertThat(TableRef.parse("delta:/var/table").format()).isEqualTo(TableFormat.DELTA);
  }
}
