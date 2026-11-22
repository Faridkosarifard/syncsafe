package io.github.faridkosarifard.syncsafe.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

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
    if (value instanceof byte[]) {
      return java.util.HexFormat.of().formatHex((byte[]) value);
    }
    return value;
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
