package io.github.faridkosarifard.syncsafe.fixtures;

/** One row of the fixture customer table used by tests and {@code syncsafe demo}. */
public final class CustomerRow {
  private final long id;
  private final String name;
  private final String city;

  public CustomerRow(long id, String name, String city) {
    this.id = id;
    this.name = name;
    this.city = city;
  }

  public long id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String city() {
    return city;
  }
}
