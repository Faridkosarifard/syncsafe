package io.github.faridkosarifard.syncsafe.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TableRefAndFormatTest {
  @Test
  void formatAliases() {
    assertThat(TableFormat.fromString("Apache-Iceberg")).isEqualTo(TableFormat.ICEBERG);
    assertThat(TableFormat.fromString("delta-lake")).isEqualTo(TableFormat.DELTA);
    assertThat(TableFormat.fromString("deltalake")).isEqualTo(TableFormat.DELTA);
    assertThat(TableFormat.ICEBERG.label()).isEqualTo("iceberg");
  }

  @Test
  void formatRejectsUnknownAndNull() {
    assertThatThrownBy(() -> TableFormat.fromString("hudi"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported");
    assertThatThrownBy(() -> TableFormat.fromString(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseStripsFileUriAndSupportsWithers() {
    TableRef ref = TableRef.parse("delta:file:///tmp/orders");
    assertThat(ref.format()).isEqualTo(TableFormat.DELTA);
    assertThat(ref.path()).isEqualTo(Path.of("/tmp/orders"));
    assertThat(ref.locationUri()).startsWith("file:");
    TableRef withVersion = ref.withVersion(4L);
    assertThat(withVersion.version()).contains(4L);
    assertThat(withVersion.toString()).contains("version=4");
    TableRef iceberg = TableRef.parse("iceberg:file:/tmp/t").withSnapshotId(9L);
    assertThat(iceberg.snapshotId()).contains(9L);
    assertThat(iceberg.toString()).contains("snapshot=9");
  }

  @Test
  void parseRejectsBadSpecs() {
    assertThatThrownBy(() -> TableRef.parse("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TableRef.parse("nocolon"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("format:path");
    assertThatThrownBy(() -> TableRef.parse("iceberg:"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path is missing");
  }
}
