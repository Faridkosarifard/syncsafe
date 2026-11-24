package io.github.faridkosarifard.syncsafe.model;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

/** Canonical tuple of the user-chosen key columns that identifies a logical row across formats. */
public final class RowKey {
  private final List<Object> values;

  private RowKey(List<Object> values) {
    this.values = Collections.unmodifiableList(new ArrayList<>(values));
  }

  public static RowKey of(List<Object> values) {
    List<Object> canonical = new ArrayList<>(values.size());
    for (Object value : values) {
      canonical.add(canonicalize(value));
    }
    return new RowKey(canonical);
  }

  /**
   * Maps a key value to one representation per logical type, so the same value read through
   * different format readers compares equal: integral numbers to {@code long}, dates to epoch days,
   * timestamps to epoch microseconds (timestamps without a zone are read as UTC), binary to
   * lowercase hex and UUIDs to their string form.
   */
  public static Object canonicalize(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
      return ((Number) value).longValue();
    }
    if (value instanceof Float) {
      return ((Float) value).doubleValue();
    }
    if (value instanceof Character) {
      return String.valueOf((Character) value);
    }
    if (value instanceof CharSequence) {
      return value.toString();
    }
    if (value instanceof byte[] bytes) {
      return HexFormat.of().formatHex(bytes);
    }
    if (value instanceof ByteBuffer buffer) {
      ByteBuffer copy = buffer.duplicate();
      byte[] bytes = new byte[copy.remaining()];
      copy.get(bytes);
      return HexFormat.of().formatHex(bytes);
    }
    if (value instanceof UUID uuid) {
      return uuid.toString();
    }
    if (value instanceof LocalDate date) {
      return date.toEpochDay();
    }
    if (value instanceof LocalDateTime dateTime) {
      return epochMicros(dateTime.toInstant(ZoneOffset.UTC));
    }
    if (value instanceof OffsetDateTime dateTime) {
      return epochMicros(dateTime.toInstant());
    }
    if (value instanceof ZonedDateTime dateTime) {
      return epochMicros(dateTime.toInstant());
    }
    if (value instanceof Instant instant) {
      return epochMicros(instant);
    }
    return value;
  }

  private static long epochMicros(Instant instant) {
    return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
  }

  public List<Object> values() {
    return values;
  }

  /** Human-readable, stable rendering used in reports. */
  public String render() {
    StringJoiner joiner = new StringJoiner(", ", "[", "]");
    for (Object value : values) {
      if (value == null) {
        joiner.add("null");
      } else if (value instanceof String str) {
        String escaped = str.replace("\\", "\\\\").replace("\"", "\\\"");
        joiner.add("\"" + escaped + "\"");
      } else {
        joiner.add(String.valueOf(value));
      }
    }
    return joiner.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RowKey other)) {
      return false;
    }
    return values.equals(other.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(values);
  }

  @Override
  public String toString() {
    return render();
  }
}
