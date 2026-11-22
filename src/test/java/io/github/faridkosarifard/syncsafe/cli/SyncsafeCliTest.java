package io.github.faridkosarifard.syncsafe.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.faridkosarifard.syncsafe.fixtures.DeltaFixtures;
import io.github.faridkosarifard.syncsafe.fixtures.FixtureData;
import io.github.faridkosarifard.syncsafe.fixtures.IcebergFixtures;
import io.github.faridkosarifard.syncsafe.model.Verdict;
import io.github.faridkosarifard.syncsafe.report.JsonReports;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncsafeCliTest {
  @TempDir Path tmp;

  @Test
  void verifyExitsZeroWhenAligned() throws Exception {
    Path source = tmp.resolve("src");
    Path target = tmp.resolve("tgt");
    DeltaFixtures.createWithDeletionVectors(source, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    IcebergFixtures.createWithPositionDeletes(
        target, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    int code =
        SyncsafeCli.run(
            new String[] {
              "verify",
              "--source",
              "delta:" + source,
              "--target",
              "iceberg:" + target,
              "--keys",
              "id"
            });
    assertThat(code).isEqualTo(ExitCodes.ALIGNED);
  }

  @Test
  void verifyExitsThreeWhenRowsResurrectAndWritesJson() throws Exception {
    Path source = tmp.resolve("src");
    Path target = tmp.resolve("tgt");
    Path json = tmp.resolve("report.json");
    DeltaFixtures.createWithDeletionVectors(source, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    IcebergFixtures.createDataOnly(target, FixtureData.CUSTOMERS);
    Captured captured =
        capture(
            () ->
                SyncsafeCli.run(
                    new String[] {
                      "verify",
                      "--source",
                      "delta:" + source,
                      "--target",
                      "iceberg:" + target,
                      "--keys",
                      "id",
                      "--json-out",
                      json.toString(),
                      "--format",
                      "json"
                    }));
    assertThat(captured.code).isEqualTo(ExitCodes.DIVERGED);
    assertThat(JsonReports.read(json).verdict()).isEqualTo(Verdict.DIVERGED);
    assertThat(captured.stdout).contains("\"verdict\" : \"diverged\"");
  }

  @Test
  void verifyExitsFourWhenTheTableIsMissing() {
    int code =
        SyncsafeCli.run(
            new String[] {
              "verify",
              "--source",
              "delta:" + tmp.resolve("missing-src"),
              "--target",
              "iceberg:" + tmp.resolve("missing-tgt"),
              "--keys",
              "id"
            });
    assertThat(code).isEqualTo(ExitCodes.INCOMPLETE);
  }

  @Test
  void verifyExitsTwoOnBadUsage() {
    int code = SyncsafeCli.run(new String[] {"verify", "--source", "nope"});
    assertThat(code).isEqualTo(ExitCodes.USAGE);
  }

  @Test
  void reportRendersAPreviousJsonFile() throws Exception {
    Path source = tmp.resolve("src");
    Path target = tmp.resolve("tgt");
    Path json = tmp.resolve("report.json");
    DeltaFixtures.createWithDeletionVectors(source, FixtureData.CUSTOMERS, FixtureData.DELETED_IDS);
    IcebergFixtures.createDataOnly(target, FixtureData.CUSTOMERS);
    SyncsafeCli.run(
        new String[] {
          "verify",
          "--source",
          "delta:" + source,
          "--target",
          "iceberg:" + target,
          "--keys",
          "id",
          "--json-out",
          json.toString()
        });
    Captured captured = capture(() -> SyncsafeCli.run(new String[] {"report", json.toString()}));
    assertThat(captured.code).isEqualTo(ExitCodes.DIVERGED);
    assertThat(captured.stdout).contains("verdict: DIVERGED").contains("resurrected=3");
  }

  @Test
  void demoBuildsBothCasesAndExitsZero() {
    Path dir = tmp.resolve("demo");
    Captured captured =
        capture(() -> SyncsafeCli.run(new String[] {"demo", "--dir", dir.toString()}));
    assertThat(captured.code).isEqualTo(ExitCodes.ALIGNED);
    assertThat(captured.stdout).contains("ALIGNED").contains("DIVERGED");
    assertThat(dir.resolve("out/aligned.json")).exists();
    assertThat(dir.resolve("out/diverged.json")).exists();
  }

  @Test
  void helpMentionsExitCodes() {
    Captured captured = capture(() -> SyncsafeCli.run(new String[] {"verify", "--help"}));
    assertThat(captured.code).isEqualTo(0);
    assertThat(captured.stdout).contains("Exit 0").contains("--keys");
  }

  @Test
  void verifyRejectsUnknownOutputFormat() throws Exception {
    Path source = tmp.resolve("fmt-src");
    Path target = tmp.resolve("fmt-tgt");
    DeltaFixtures.createDataOnly(source, FixtureData.CUSTOMERS);
    IcebergFixtures.createDataOnly(target, FixtureData.CUSTOMERS);
    int code =
        SyncsafeCli.run(
            new String[] {
              "verify",
              "--source",
              "delta:" + source,
              "--target",
              "iceberg:" + target,
              "--keys",
              "id",
              "--format",
              "xml"
            });
    assertThat(code).isEqualTo(ExitCodes.USAGE);
  }

  @Test
  void reportRendersJsonAndRejectsUnknownFormat() throws Exception {
    Path source = tmp.resolve("r-src");
    Path target = tmp.resolve("r-tgt");
    Path json = tmp.resolve("ok.json");
    DeltaFixtures.createDataOnly(source, FixtureData.CUSTOMERS);
    IcebergFixtures.createDataOnly(target, FixtureData.CUSTOMERS);
    SyncsafeCli.run(
        new String[] {
          "verify",
          "--source",
          "delta:" + source,
          "--target",
          "iceberg:" + target,
          "--keys",
          "id",
          "--json-out",
          json.toString()
        });
    int bad = SyncsafeCli.run(new String[] {"report", json.toString(), "--format", "xml"});
    assertThat(bad).isEqualTo(ExitCodes.USAGE);
    Captured captured =
        capture(
            () -> SyncsafeCli.run(new String[] {"report", json.toString(), "--format", "json"}));
    assertThat(captured.code).isEqualTo(ExitCodes.ALIGNED);
    assertThat(captured.stdout).contains("verdict");
  }

  private static Captured capture(java.util.function.IntSupplier action) {
    PrintStream originalOut = System.out;
    PrintStream originalErr = System.err;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    try {
      int code = action.getAsInt();
      return new Captured(
          code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }

  private record Captured(int code, String stdout, String stderr) {}
}
