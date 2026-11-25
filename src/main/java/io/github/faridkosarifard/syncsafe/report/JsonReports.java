package io.github.faridkosarifard.syncsafe.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.faridkosarifard.syncsafe.model.Divergence;
import io.github.faridkosarifard.syncsafe.model.SideSummary;
import io.github.faridkosarifard.syncsafe.model.TableFormat;
import io.github.faridkosarifard.syncsafe.model.Verdict;
import io.github.faridkosarifard.syncsafe.model.VerificationReport;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JSON serialization for {@link VerificationReport} (schema 1.0). */
public final class JsonReports {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private JsonReports() {}

  public static void write(VerificationReport report, Path path) throws IOException {
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try (Writer writer = Files.newBufferedWriter(path)) {
      write(report, writer);
    }
  }

  public static void write(VerificationReport report, Writer writer) throws IOException {
    MAPPER.writeValue(writer, toJson(report));
  }

  public static String toString(VerificationReport report) {
    try {
      return MAPPER.writeValueAsString(toJson(report));
    } catch (IOException e) {
      throw new IllegalStateException("failed to serialize report", e);
    }
  }

  public static VerificationReport read(Path path) throws IOException {
    try (Reader reader = Files.newBufferedReader(path)) {
      return read(reader);
    }
  }

  public static VerificationReport read(Reader reader) throws IOException {
    JsonNode root = MAPPER.readTree(reader);
    if (root == null || !root.isObject()) {
      throw new IOException("report is not a JSON object");
    }
    return fromJson((ObjectNode) root);
  }

  public static ObjectNode toJson(VerificationReport report) {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("schema_version", report.schemaVersion());
    root.put("tool", report.tool());
    root.put("tool_version", report.toolVersion());
    root.put("generated_at", report.generatedAt().toString());
    ArrayNode keys = root.putArray("key_columns");
    report.keyColumns().forEach(keys::add);
    root.set("source", sideJson(report.source()));
    root.set("target", sideJson(report.target()));
    root.put("verdict", report.verdict().label());
    ObjectNode summary = root.putObject("summary");
    for (Map.Entry<String, Long> entry : report.summaryCounts().entrySet()) {
      summary.put(entry.getKey(), entry.getValue());
    }
    root.put("sample_limit", report.sampleLimit());
    ArrayNode divergences = root.putArray("divergences");
    for (Divergence divergence : report.divergences()) {
      ObjectNode node = divergences.addObject();
      node.put("kind", divergence.kind().label());
      node.put("key", divergence.key().render());
      ArrayNode values = node.putArray("key_values");
      for (Object value : divergence.key().values()) {
        if (value == null) {
          values.addNull();
        } else if (value instanceof Long longValue) {
          values.add(longValue);
        } else if (value instanceof Integer intValue) {
          values.add(intValue);
        } else if (value instanceof Double doubleValue) {
          values.add(doubleValue);
        } else if (value instanceof Boolean boolValue) {
          values.add(boolValue);
        } else if (value instanceof BigDecimal decimal) {
          // Strings, not JSON numbers: IEEE double parsers would lose precision.
          values.add(decimal.toPlainString());
        } else {
          values.add(String.valueOf(value));
        }
      }
      node.put("source_count", divergence.sourceCount());
      node.put("target_count", divergence.targetCount());
    }
    if (report.hint().isPresent()) {
      root.put("hint", report.hint().get());
    } else {
      root.putNull("hint");
    }
    return root;
  }

  private static ObjectNode sideJson(SideSummary side) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("format", side.format().label());
    node.put("location", side.location());
    if (side.snapshotId().isPresent()) {
      node.put("snapshot_id", side.snapshotId().get());
    } else {
      node.putNull("snapshot_id");
    }
    if (side.version().isPresent()) {
      node.put("version", side.version().get());
    } else {
      node.putNull("version");
    }
    ArrayNode keys = node.putArray("key_columns");
    side.keyColumns().forEach(keys::add);
    node.put("live_row_count", side.liveRowCount());
    node.put("physical_row_count", side.physicalRowCount());
    node.put("deleted_row_count", side.deletedRowCount());
    if (side.error().isPresent()) {
      node.put("error", side.error().get());
    } else {
      node.putNull("error");
    }
    return node;
  }

  private static VerificationReport fromJson(ObjectNode root) throws IOException {
    Instant generatedAt = Instant.parse(text(root, "generated_at"));
    List<String> keys = stringList(root.get("key_columns"));
    SideSummary source = sideFromJson(root.with("source"));
    SideSummary target = sideFromJson(root.with("target"));
    Verdict verdict = Verdict.valueOf(text(root, "verdict").toUpperCase());
    ObjectNode summary = root.with("summary");
    long resurrected = summary.path("resurrected").asLong(0);
    long missing = summary.path("missing").asLong(0);
    long mismatch = summary.path("count_mismatch").asLong(0);
    int sampleLimit = root.path("sample_limit").asInt(25);
    List<io.github.faridkosarifard.syncsafe.model.Divergence> divergences = new ArrayList<>();
    JsonNode array = root.get("divergences");
    if (array != null && array.isArray()) {
      for (JsonNode item : array) {
        io.github.faridkosarifard.syncsafe.model.DivergenceKind kind =
            io.github.faridkosarifard.syncsafe.model.DivergenceKind.valueOf(
                text((ObjectNode) item, "kind").toUpperCase());
        List<Object> values = new ArrayList<>();
        JsonNode keyValues = item.get("key_values");
        if (keyValues != null && keyValues.isArray()) {
          for (JsonNode value : keyValues) {
            values.add(jsonValue(value));
          }
        }
        divergences.add(
            new io.github.faridkosarifard.syncsafe.model.Divergence(
                kind,
                io.github.faridkosarifard.syncsafe.model.RowKey.of(values),
                item.path("source_count").asLong(),
                item.path("target_count").asLong()));
      }
    }
    String hint = root.hasNonNull("hint") ? root.get("hint").asText() : null;
    return new VerificationReport(
        generatedAt,
        keys,
        source,
        target,
        verdict,
        resurrected,
        missing,
        mismatch,
        divergences,
        sampleLimit,
        hint);
  }

  private static SideSummary sideFromJson(ObjectNode node) throws IOException {
    TableFormat format = TableFormat.fromString(text(node, "format"));
    String location = text(node, "location");
    Long snapshot = node.hasNonNull("snapshot_id") ? node.get("snapshot_id").asLong() : null;
    Long version = node.hasNonNull("version") ? node.get("version").asLong() : null;
    List<String> keys = stringList(node.get("key_columns"));
    long live = node.path("live_row_count").asLong();
    long physical = node.path("physical_row_count").asLong();
    long deleted = node.path("deleted_row_count").asLong();
    String error = node.hasNonNull("error") ? node.get("error").asText() : null;
    return new SideSummary(
        format, location, snapshot, version, keys, live, physical, deleted, error);
  }

  private static List<String> stringList(JsonNode node) {
    List<String> values = new ArrayList<>();
    if (node != null && node.isArray()) {
      node.forEach(item -> values.add(item.asText()));
    }
    return values;
  }

  private static String text(ObjectNode node, String field) throws IOException {
    if (!node.hasNonNull(field)) {
      throw new IOException("missing required field: " + field);
    }
    return node.get(field).asText();
  }

  private static Object jsonValue(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    if (node.isIntegralNumber()) {
      return node.longValue();
    }
    if (node.isFloatingPointNumber()) {
      return node.doubleValue();
    }
    return node.asText();
  }
}
