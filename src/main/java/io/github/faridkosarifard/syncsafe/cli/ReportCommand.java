package io.github.faridkosarifard.syncsafe.cli;

import io.github.faridkosarifard.syncsafe.model.VerificationReport;
import io.github.faridkosarifard.syncsafe.report.JsonReports;
import io.github.faridkosarifard.syncsafe.report.TextReports;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Render a previously written JSON report as text (or pretty JSON). */
@Command(
    name = "report",
    mixinStandardHelpOptions = true,
    description =
        "Render a Syncsafe JSON report. Exit codes match verify: 0 aligned, 3 diverged, 4"
            + " incomplete.")
public final class ReportCommand implements Callable<Integer> {
  @Parameters(index = "0", paramLabel = "REPORT", description = "Path to a JSON report")
  Path reportPath;

  @Option(
      names = {"--format", "-f"},
      defaultValue = "text",
      description = "Stdout format: text (default) or json")
  String outputFormat;

  @Override
  public Integer call() throws Exception {
    VerificationReport report = JsonReports.read(reportPath);
    if ("json".equalsIgnoreCase(outputFormat)) {
      System.out.println(JsonReports.toString(report));
    } else if ("text".equalsIgnoreCase(outputFormat)) {
      System.out.print(TextReports.toString(report));
    } else {
      throw new IllegalArgumentException(
          "unsupported --format: " + outputFormat + " (expected text or json)");
    }
    return ExitCodes.of(report.verdict());
  }
}
