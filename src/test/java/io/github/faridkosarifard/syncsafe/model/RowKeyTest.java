package io.github.faridkosarifard.syncsafe.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
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

  @Test
  void canonicalizesTemporalBinaryAndUuidValues() {
    assertThat(RowKey.canonicalize(LocalDate.of(1970, 1, 11))).isEqualTo(10L);
    assertThat(RowKey.canonicalize(LocalDateTime.of(1970, 1, 1, 0, 0, 1))).isEqualTo(1_000_000L);
    assertThat(
            RowKey.canonicalize(OffsetDateTime.of(1970, 1, 1, 1, 0, 0, 0, ZoneOffset.ofHours(1))))
        .isEqualTo(0L);
    assertThat(RowKey.canonicalize(LocalDateTime.of(1969, 12, 31, 23, 59, 59, 999_999_000)))
        .isEqualTo(-1L);

    ByteBuffer buffer = ByteBuffer.wrap(new byte[] {0x0a, (byte) 0xff});
    assertThat(RowKey.canonicalize(buffer)).isEqualTo("0aff");
    assertThat(buffer.remaining()).isEqualTo(2);
    assertThat(RowKey.of(List.of(buffer)))
        .isEqualTo(RowKey.of(List.of(new byte[] {0x0a, (byte) 0xff})));

    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    assertThat(RowKey.canonicalize(uuid)).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
  }
}
