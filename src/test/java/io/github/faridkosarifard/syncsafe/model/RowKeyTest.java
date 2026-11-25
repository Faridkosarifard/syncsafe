package io.github.faridkosarifard.syncsafe.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
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

  @Test
  void timestampsBeforeTheEpochAreFloored() {
    assertThat(RowKey.canonicalize(LocalDateTime.of(1969, 12, 31, 23, 59, 59, 999_999_500)))
        .isEqualTo(new BigDecimal("-0.500"));
    assertThat(RowKey.canonicalize(LocalDateTime.of(1969, 12, 31, 23, 59, 59, 999_998_500)))
        .isEqualTo(new BigDecimal("-1.500"));
    assertThat(RowKey.canonicalize(LocalDateTime.of(1969, 12, 31, 23, 59, 59, 999_998_000)))
        .isEqualTo(-2L);
    assertThat(RowKey.canonicalize(LocalDateTime.of(1, 1, 1, 0, 0)))
        .isEqualTo(-62_135_596_800_000_000L);
  }

  @Test
  void nanosecondTimestampsKeepSubMicrosecondPrecision() {
    LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
    RowKey a = RowKey.of(List.of(base.plusNanos(100)));
    RowKey b = RowKey.of(List.of(base.plusNanos(101)));
    assertThat(a).isNotEqualTo(b);
    assertThat(a.values().get(0)).isEqualTo(new BigDecimal("1767225600000000.100"));
    assertThat(RowKey.of(List.of(base.minusNanos(1))))
        .isNotEqualTo(RowKey.of(List.of(base.minusNanos(2))));
  }

  @Test
  void deltaMicrosAndIcebergNanosAgreeForTheSameInstant() {
    long deltaMicros = 1_767_225_600_123_456L;
    OffsetDateTime icebergNanos =
        OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 123_456_000, ZoneOffset.UTC);
    LocalDateTime icebergNtzNanos = LocalDateTime.of(2026, 1, 1, 0, 0, 0, 123_456_000);
    assertThat(RowKey.of(List.of(icebergNanos))).isEqualTo(RowKey.of(List.of(deltaMicros)));
    assertThat(RowKey.of(List.of(icebergNtzNanos))).isEqualTo(RowKey.of(List.of(deltaMicros)));
    long preEpochMicros = -1L;
    assertThat(RowKey.of(List.of(LocalDateTime.of(1969, 12, 31, 23, 59, 59, 999_999_000))))
        .isEqualTo(RowKey.of(List.of(preEpochMicros)));
  }

  @Test
  void timestampKeysCoverTheFullLongMicrosecondRange() {
    // Long.MIN_VALUE µs is -9223372036855s + 224192µs. multiplyExact(seconds, 1e6) overflows
    // here even though the final value fits; the same happens at Long.MAX_VALUE.
    Instant minMicros = Instant.ofEpochSecond(-9_223_372_036_855L, 224_192_000L);
    Instant justAboveMin = Instant.ofEpochSecond(-9_223_372_036_855L, 224_193_000L);
    Instant maxMicros = Instant.ofEpochSecond(9_223_372_036_854L, 775_807_000L);
    Instant belowMin = Instant.ofEpochSecond(-9_223_372_036_855L, 224_191_000L);
    Instant aboveMax = Instant.ofEpochSecond(9_223_372_036_854L, 775_808_000L);

    assertThat(RowKey.canonicalize(minMicros)).isEqualTo(Long.MIN_VALUE);
    assertThat(RowKey.canonicalize(justAboveMin)).isEqualTo(Long.MIN_VALUE + 1L);
    assertThat(RowKey.canonicalize(maxMicros)).isEqualTo(Long.MAX_VALUE);
    assertThat(RowKey.of(List.of(minMicros))).isEqualTo(RowKey.of(List.of(Long.MIN_VALUE)));
    assertThat(RowKey.of(List.of(maxMicros))).isEqualTo(RowKey.of(List.of(Long.MAX_VALUE)));

    assertThat(RowKey.canonicalize(belowMin)).isEqualTo(new BigDecimal("-9223372036854775809"));
    assertThat(RowKey.canonicalize(aboveMax)).isEqualTo(new BigDecimal("9223372036854775808"));
    assertThat(RowKey.of(List.of(belowMin))).isNotEqualTo(RowKey.of(List.of(minMicros)));
    assertThat(RowKey.of(List.of(aboveMax))).isNotEqualTo(RowKey.of(List.of(maxMicros)));

    Instant minPlusNanos = Instant.ofEpochSecond(-9_223_372_036_855L, 224_192_100L);
    assertThat(RowKey.canonicalize(minPlusNanos))
        .isEqualTo(new BigDecimal("-9223372036854775807.900"));
    assertThat(RowKey.of(List.of(minPlusNanos))).isNotEqualTo(RowKey.of(List.of(minMicros)));

    assertThat(RowKey.canonicalize(Instant.MIN)).isInstanceOf(BigDecimal.class);
    assertThat(RowKey.canonicalize(Instant.MAX)).isInstanceOf(BigDecimal.class);
    assertThat(RowKey.of(List.of(Instant.MIN))).isNotEqualTo(RowKey.of(List.of(Instant.MAX)));
  }
}
