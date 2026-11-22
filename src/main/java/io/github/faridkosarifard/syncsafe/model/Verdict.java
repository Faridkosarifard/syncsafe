package io.github.faridkosarifard.syncsafe.model;

/** Overall result of comparing two live row sets. */
public enum Verdict {
  /** Live keys and multiplicities match. */
  ALIGNED,
  /** At least one resurrected, missing, or count-mismatched key was found. */
  DIVERGED,
  /** A side could not be read completely, so a clean pass must not be claimed. */
  INCOMPLETE;

  public String label() {
    return name().toLowerCase();
  }
}
