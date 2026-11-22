package io.github.faridkosarifard.syncsafe.cli;

import io.github.faridkosarifard.syncsafe.model.Verdict;

/**
 * Process exit codes used by the Syncsafe CLI.
 *
 * <ul>
 *   <li>0 — the live sets are aligned
 *   <li>2 — usage or argument error (Picocli default)
 *   <li>3 — divergences were found
 *   <li>4 — a side could not be read completely
 * </ul>
 */
public final class ExitCodes {
  public static final int ALIGNED = 0;
  public static final int USAGE = 2;
  public static final int DIVERGED = 3;
  public static final int INCOMPLETE = 4;

  private ExitCodes() {}

  public static int of(Verdict verdict) {
    return switch (verdict) {
      case ALIGNED -> ALIGNED;
      case DIVERGED -> DIVERGED;
      case INCOMPLETE -> INCOMPLETE;
    };
  }
}
