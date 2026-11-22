package io.github.faridkosarifard.syncsafe.report;

import io.github.faridkosarifard.syncsafe.model.Divergence;
import io.github.faridkosarifard.syncsafe.model.SideSummary;
import io.github.faridkosarifard.syncsafe.model.VerificationReport;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Human-readable rendering of a {@link VerificationReport}. */
public final class TextReports {
  private TextReports() {}

  public static void write(VerificationReport report, Path path) throws IOException {
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(path, toString(report));
  }

  public static void write(VerificationReport report, Writer writer) throws IOException {
    writer.write(toString(report));
    writer.flush();
  }

  public static String toString(VerificationReport report) {
    StringBuilder sb = new StringBuilder();
    sb.append("Syncsafe ").append(report.toolVersion()).append("  verdict: ");
    sb.append(report.verdict().label().toUpperCase(Locale.ROOT)).append('\n');
    sb.append("Generated at ").append(report.generatedAt()).append('\n');
    sb.append("Key columns: ").append(String.join(", ", report.keyColumns())).append('\n');
    sb.append('\n');
    appendSide(sb, "Source", report.source());
    sb.append('\n');
    appendSide(sb, "Target", report.target());
    sb.append('\n');
    sb.append("Divergences: ")
        .append(report.totalDivergences())
        .append("  (resurrected=")
        .append(report.resurrectedCount())
        .append(", missing=")
        .append(report.missingCount())
        .append(", count_mismatch=")
        .append(report.countMismatchCount())
        .append(")\n");
    if (!report.divergences().isEmpty()) {
      sb.append("Sample (up to ").append(report.sampleLimit()).append("):\n");
      for (Divergence divergence : report.divergences()) {
        sb.append("  - ")
            .append(divergence.kind().label())
            .append("  key=")
            .append(divergence.key().render())
            .append("  source=")
            .append(divergence.sourceCount())
            .append("  target=")
            .append(divergence.targetCount())
            .append('\n');
      }
    }
    report.hint().ifPresent(hint -> sb.append("Hint: ").append(hint).append('\n'));
    return sb.toString();
  }

  private static void appendSide(StringBuilder sb, String title, SideSummary side) {
    sb.append(title)
        .append(": ")
        .append(side.format().label())
        .append("  ")
        .append(side.location());
    side.snapshotId().ifPresent(id -> sb.append("  snapshot=").append(id));
    side.version().ifPresent(v -> sb.append("  version=").append(v));
    sb.append('\n');
    sb.append("  live=").append(side.liveRowCount());
    sb.append("  physical=").append(side.physicalRowCount());
    sb.append("  deleted=").append(side.deletedRowCount()).append('\n');
    side.error().ifPresent(err -> sb.append("  error: ").append(err).append('\n'));
  }
}
