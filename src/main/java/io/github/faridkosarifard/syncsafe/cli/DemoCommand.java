package io.github.faridkosarifard.syncsafe.cli;

import io.github.faridkosarifard.syncsafe.compare.LiveSetComparator;
import io.github.faridkosarifard.syncsafe.fixtures.DeltaFixtures;
import io.github.faridkosarifard.syncsafe.fixtures.FixtureData;
import io.github.faridkosarifard.syncsafe.fixtures.IcebergFixtures;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import io.github.faridkosarifard.syncsafe.model.VerificationReport;
import io.github.faridkosarifard.syncsafe.read.TableReaders;
import io.github.faridkosarifard.syncsafe.report.JsonReports;
import io.github.faridkosarifard.syncsafe.report.TextReports;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Builds two fixture pairs on the local filesystem and runs {@code verify} on both: an aligned
 * conversion (Delta deletion vectors translated to Iceberg position deletes) and a diverged
 * conversion (the same data files, but the Iceberg side has no delete files).
 */
@Command(
    name = "demo",
    mixinStandardHelpOptions = true,
    description =
        "Create local fixture tables and run verify on an aligned pair and on a pair that"
            + " simulates a conversion that dropped deletion vectors.")
public final class DemoCommand implements Callable<Integer> {
  @Option(
      names = {"--dir", "-d"},
      required = true,
      description = "Directory that will hold the fixture tables and reports")
  Path dir;

  @Override
  public Integer call() throws Exception {
    Path root = dir.toAbsolutePath().normalize();
    Files.createDirectories(root);
    Path alignedSource = root.resolve("aligned/source-delta");
    Path alignedTarget = root.resolve("aligned/target-iceberg");
    Path divergedSource = root.resolve("diverged/source-delta");
    Path divergedTarget = root.resolve("diverged/target-iceberg");
    Path out = root.resolve("out");
    Files.createDirectories(out);

    DeltaFixtures.createWithDeletionVectors(
        alignedSource, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    IcebergFixtures.createWithPositionDeletes(
        alignedTarget, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    DeltaFixtures.createWithDeletionVectors(
        divergedSource, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    IcebergFixtures.createDataOnly(divergedTarget, FixtureData.CUSTOMERS);

    VerificationReport aligned = verify(alignedSource, alignedTarget, "delta", "iceberg");
    VerificationReport diverged = verify(divergedSource, divergedTarget, "delta", "iceberg");
    JsonReports.write(aligned, out.resolve("aligned.json"));
    TextReports.write(aligned, out.resolve("aligned.txt"));
    JsonReports.write(diverged, out.resolve("diverged.json"));
    TextReports.write(diverged, out.resolve("diverged.txt"));

    System.out.println("Syncsafe demo fixtures written under " + root);
    System.out.println();
    System.out.println("Aligned conversion (Delta deletion vectors -> Iceberg position deletes):");
    System.out.print(TextReports.toString(aligned));
    System.out.println("Diverged conversion (Iceberg metadata points at the data files only):");
    System.out.print(TextReports.toString(diverged));
    System.out.println("Reports: " + out);
    System.out.println(
        "Re-run: syncsafe verify --source delta:"
            + divergedSource
            + " --target iceberg:"
            + divergedTarget
            + " --keys id");
    // The demo itself succeeds if both cases behaved as expected.
    if (aligned.verdict() != io.github.faridkosarifard.syncsafe.model.Verdict.ALIGNED) {
      return ExitCodes.INCOMPLETE;
    }
    if (diverged.verdict() != io.github.faridkosarifard.syncsafe.model.Verdict.DIVERGED) {
      return ExitCodes.INCOMPLETE;
    }
    return ExitCodes.ALIGNED;
  }

  private static VerificationReport verify(
      Path source, Path target, String sourceFormat, String targetFormat) {
    LiveRowSet sourceSet =
        TableReaders.readQuietly(TableRef.parse(sourceFormat + ":" + source), FixtureData.KEYS);
    LiveRowSet targetSet =
        TableReaders.readQuietly(TableRef.parse(targetFormat + ":" + target), FixtureData.KEYS);
    return LiveSetComparator.compare(sourceSet, targetSet);
  }
}
