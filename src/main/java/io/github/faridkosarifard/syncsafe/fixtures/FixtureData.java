package io.github.faridkosarifard.syncsafe.fixtures;

import java.util.List;
import java.util.Set;

/** Shared fixture rows: ten customers, three of which are deleted in the "after DELETE" cases. */
public final class FixtureData {
  public static final List<String> KEYS = List.of("id");

  public static final List<CustomerRow> CUSTOMERS =
      List.of(
          new CustomerRow(1, "Ada", "Berlin"),
          new CustomerRow(2, "Grace", "Hamburg"),
          new CustomerRow(3, "Alan", "Munich"),
          new CustomerRow(4, "Katherine", "Cologne"),
          new CustomerRow(5, "Donald", "Frankfurt"),
          new CustomerRow(6, "Barbara", "Stuttgart"),
          new CustomerRow(7, "Edsger", "Dresden"),
          new CustomerRow(8, "Frances", "Leipzig"),
          new CustomerRow(9, "Linus", "Bremen"),
          new CustomerRow(10, "Radia", "Hannover"));

  public static final Set<Long> DELETED_IDS = Set.of(2L, 5L, 9L);

  private FixtureData() {}

  public static List<CustomerRow> liveRows() {
    return CUSTOMERS.stream().filter(row -> !DELETED_IDS.contains(row.id())).toList();
  }
}
