package io.github.faridkosarifard.syncsafe.cli;

import io.github.faridkosarifard.syncsafe.compare.LiveSetComparator;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import io.github.faridkosarifard.syncsafe.model.VerificationReport;
import io.github.faridkosarifard.syncsafe.read.TableReaders;
import io.github.faridkosarifard.syncsafe.report.JsonReports;
import io.github.faridkosarifard.syncsafe.report.TextReports;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Compare the live row sets of a source table and a target table. */
@Command(
    name = "verify",
    mixinStandardHelpOptions = true,
    description =
        "Compute the live row set of two lakehouse tables (Iceberg or Delta) and report"
            + " divergences. Exit 0 if they agree, 3 if they diverge, 4 if a side could not be"
            + " read.")
public final class VerifyCommand implements Callable<Integer> {
  @Option(
      names = "--source",
      required = true,
      description =
          "Source table as format:path, for example iceberg:/tmp/orders or delta:/tmp/orders")
  String sourceSpec;

  @Option(
      names = "--target",
      required = true,
      description =
          "Target table as format:path, for example iceberg:/tmp/orders or delta:/tmp/orders")
  String targetSpec;

  @Option(
      names = "--source-snapshot",
      description = "Iceberg snapshot id on the source (default: current snapshot)")
  Long sourceSnapshot;

  @Option(
      names = "--target-snapshot",
      description = "Iceberg snapshot id on the target (default: current snapshot)")
  Long targetSnapshot;

  @Option(
      names = "--source-version",
      description = "Delta table version on the source (default: latest)")
  Long sourceVersion;

  @Option(
      names = "--target-version",
      description = "Delta table version on the target (default: latest)")
  Long targetVersion;

  @Option(
      names = "--keys",
      required = true,
      split = ",",
      paramLabel = "COLUMN",
      description = "Comma-separated key columns that identify a logical row")
  List<String> keys;

  @Option(
      names = {"--format", "-f"},
      defaultValue = "text",
      description = "Stdout format: text (default) or json")
  String outputFormat;

  @Option(names = "--json-out", description = "Write the machine-readable report to this file")
  Path jsonOut;

  @Option(names = "--text-out", description = "Write the human-readable report to this file")
  Path textOut;

  @Option(
      names = "--sample-limit",
      defaultValue = "25",
      description = "Maximum number of diverging keys to include in the report (default: 25)")
  int sampleLimit;

  @Override
  public Integer call() throws IOException {
    List<String> keyColumns = normalizeKeys(keys);
    TableRef source = TableRef.parse(sourceSpec, sourceSnapshot, sourceVersion);
    TableRef target = TableRef.parse(targetSpec, targetSnapshot, targetVersion);
    LiveRowSet sourceSet = TableReaders.readQuietly(source, keyColumns);
    LiveRowSet targetSet = TableReaders.readQuietly(target, keyColumns);
    VerificationReport report = LiveSetComparator.compare(sourceSet, targetSet, sampleLimit);
    write(report);
    return ExitCodes.of(report.verdict());
  }

  private void write(VerificationReport report) throws IOException {
    if (jsonOut != null) {
      JsonReports.write(report, jsonOut);
    }
    if (textOut != null) {
      TextReports.write(report, textOut);
    }
    if ("json".equalsIgnoreCase(outputFormat)) {
      System.out.println(JsonReports.toString(report));
    } else if ("text".equalsIgnoreCase(outputFormat)) {
      System.out.print(TextReports.toString(report));
    } else {
      throw new IllegalArgumentException(
          "unsupported --format: " + outputFormat + " (expected text or json)");
    }
  }

  static List<String> normalizeKeys(List<String> raw) {
    List<String> keys = new ArrayList<>();
    for (String item : raw) {
      if (item == null) {
        continue;
      }
      for (String part : item.split(",")) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty() && !keys.contains(trimmed)) {
          keys.add(trimmed);
        }
      }
    }
    if (keys.isEmpty()) {
      throw new IllegalArgumentException("at least one --keys column is required");
    }
    return keys;
  }
}
