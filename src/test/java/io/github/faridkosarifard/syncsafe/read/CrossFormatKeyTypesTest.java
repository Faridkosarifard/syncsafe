package io.github.faridkosarifard.syncsafe.read;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.faridkosarifard.syncsafe.compare.LiveSetComparator;
import io.github.faridkosarifard.syncsafe.model.LiveRowSet;
import io.github.faridkosarifard.syncsafe.model.TableRef;
import io.github.faridkosarifard.syncsafe.model.Verdict;
import io.github.faridkosarifard.syncsafe.model.VerificationReport;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.parquet.GenericParquetWriter;
import org.apache.iceberg.hadoop.HadoopFileIO;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The same rows written as Delta and as Iceberg must align when keyed on non-numeric types. */
class CrossFormatKeyTypesTest {
  private static final List<String> KEY = List.of("k");

  @TempDir Path tmp;

  @Test
  void dateKeys() throws Exception {
    assertCrossFormat(
        Types.DateType.get(),
        "date",
        List.of(LocalDate.of(2026, 1, 1), LocalDate.of(1969, 12, 31), LocalDate.of(2026, 11, 24)),
        LocalDate.of(2000, 2, 29));
  }

  @Test
  void timestampWithZoneKeys() throws Exception {
    assertCrossFormat(
        Types.TimestampType.withZone(),
        "timestamp",
        List.of(
            OffsetDateTime.of(2026, 1, 1, 12, 30, 0, 123_456_000, ZoneOffset.UTC),
            OffsetDateTime.of(1969, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2026, 11, 24, 0, 0, 0, 0, ZoneOffset.UTC)),
        OffsetDateTime.of(2026, 1, 1, 12, 30, 0, 123_457_000, ZoneOffset.UTC));
  }

  @Test
  void timestampWithoutZoneKeys() throws Exception {
    assertCrossFormat(
        Types.TimestampType.withoutZone(),
        "timestamp_ntz",
        List.of(
            LocalDateTime.of(2026, 1, 1, 12, 30, 0, 123_456_000),
            LocalDateTime.of(1969, 12, 31, 23, 59, 59),
            LocalDateTime.of(2026, 11, 24, 0, 0)),
        LocalDateTime.of(1969, 12, 31, 23, 59, 58, 999_999_000));
  }

  @Test
  void binaryKeys() throws Exception {
    assertCrossFormat(
        Types.BinaryType.get(),
        "binary",
        List.of(
            ByteBuffer.wrap(new byte[] {0x00, 0x01}),
            ByteBuffer.wrap(new byte[] {(byte) 0xff}),
            ByteBuffer.wrap(new byte[] {0x7f, 0x10, 0x20})),
        ByteBuffer.wrap(new byte[] {0x00, 0x02}));
  }

  @Test
  void uuidKeysMatchLowercaseDeltaStrings() throws Exception {
    List<UUID> uuids =
        List.of(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            UUID.fromString("abcdef00-0000-4000-8000-00000000000a"));
    Path iceberg = tmp.resolve("uuid-iceberg");
    writeIceberg(iceberg, "2", keySchema(Types.UUIDType.get()), new ArrayList<>(uuids));
    List<Object> lower = new ArrayList<>();
    List<Object> upper = new ArrayList<>();
    for (UUID uuid : uuids) {
      lower.add(uuid.toString());
      upper.add(uuid.toString().toUpperCase(Locale.ROOT));
    }
    Path deltaLower = tmp.resolve("uuid-delta-lower");
    Path deltaUpper = tmp.resolve("uuid-delta-upper");
    writeDelta(deltaLower, keySchema(Types.StringType.get()), "string", lower);
    writeDelta(deltaUpper, keySchema(Types.StringType.get()), "string", upper);

    assertThat(compare("delta:" + deltaLower, "iceberg:" + iceberg).verdict())
        .isEqualTo(Verdict.ALIGNED);
    // Keys are matched exactly: an uppercase string is a different key from the canonical UUID.
    VerificationReport upperCase = compare("delta:" + deltaUpper, "iceberg:" + iceberg);
    assertThat(upperCase.verdict()).isEqualTo(Verdict.DIVERGED);
    assertThat(upperCase.resurrectedCount()).isEqualTo(2);
    assertThat(upperCase.missingCount()).isEqualTo(2);
  }

  @Test
  void icebergNanosecondTimestampsMatchDeltaMicrosForTheSameInstant() throws Exception {
    List<Object> deltaKeys =
        List.of(
            LocalDateTime.of(2026, 1, 1, 0, 0, 0, 123_456_000),
            LocalDateTime.of(1969, 12, 31, 23, 59, 59, 999_999_000));
    Path iceberg = tmp.resolve("ns-iceberg");
    Path delta = tmp.resolve("ns-delta");
    writeIceberg(iceberg, "3", keySchema(Types.TimestampNanoType.withoutZone()), deltaKeys);
    writeDelta(delta, keySchema(Types.TimestampType.withoutZone()), "timestamp_ntz", deltaKeys);

    assertThat(compare("delta:" + delta, "iceberg:" + iceberg).verdict())
        .isEqualTo(Verdict.ALIGNED);
  }

  @Test
  void icebergNanosecondTimestampsThatDifferBelowOneMicrosecondDiverge() throws Exception {
    LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0);
    Path source = tmp.resolve("ns-source");
    Path target = tmp.resolve("ns-target");
    Schema schema = keySchema(Types.TimestampNanoType.withoutZone());
    writeIceberg(source, "3", schema, List.of(base.plusNanos(100), base.plusNanos(101)));
    writeIceberg(target, "3", schema, List.of(base.plusNanos(105), base.plusNanos(107)));

    VerificationReport report = compare("iceberg:" + source, "iceberg:" + target);
    assertThat(report.verdict()).isEqualTo(Verdict.DIVERGED);
    assertThat(report.resurrectedCount()).isEqualTo(2);
    assertThat(report.missingCount()).isEqualTo(2);
  }

  @Test
  void icebergNanosecondTimestampWithSubMicrosecondPartDivergesFromDeltaMicros() throws Exception {
    LocalDateTime exact = LocalDateTime.of(2026, 1, 1, 0, 0, 0, 123_456_000);
    LocalDateTime withNanos = exact.plusNanos(100);
    Path iceberg = tmp.resolve("ns-sub-iceberg");
    Path delta = tmp.resolve("ns-sub-delta");
    writeIceberg(
        iceberg, "3", keySchema(Types.TimestampNanoType.withoutZone()), List.of(withNanos));
    writeDelta(
        delta, keySchema(Types.TimestampType.withoutZone()), "timestamp_ntz", List.of(exact));

    VerificationReport report = compare("delta:" + delta, "iceberg:" + iceberg);
    assertThat(report.verdict()).isEqualTo(Verdict.DIVERGED);
    assertThat(report.resurrectedCount()).isEqualTo(1);
    assertThat(report.missingCount()).isEqualTo(1);
  }

  /**
   * Writes the same keys to Delta and Iceberg and expects ALIGNED, then changes one key on the
   * Iceberg side and expects exactly one resurrected and one missing key.
   */
  private void assertCrossFormat(
      Type icebergType, String deltaType, List<Object> keys, Object replacement)
      throws IOException {
    Schema schema = keySchema(icebergType);
    Path delta = tmp.resolve("delta");
    Path iceberg = tmp.resolve("iceberg");
    Path changed = tmp.resolve("iceberg-changed");
    writeDelta(delta, schema, deltaType, keys);
    writeIceberg(iceberg, "2", schema, keys);
    List<Object> changedKeys = new ArrayList<>(keys);
    changedKeys.set(0, replacement);
    writeIceberg(changed, "2", schema, changedKeys);

    LiveRowSet source = TableReaders.readQuietly(TableRef.parse("delta:" + delta), KEY);
    LiveRowSet target = TableReaders.readQuietly(TableRef.parse("iceberg:" + iceberg), KEY);
    assertThat(source.error()).isEmpty();
    assertThat(target.error()).isEmpty();
    assertThat(source.counts()).hasSize(keys.size());
    assertThat(target.counts()).hasSize(keys.size());
    assertThat(LiveSetComparator.compare(source, target).verdict()).isEqualTo(Verdict.ALIGNED);

    VerificationReport diverged = compare("delta:" + delta, "iceberg:" + changed);
    assertThat(diverged.verdict()).isEqualTo(Verdict.DIVERGED);
    assertThat(diverged.resurrectedCount()).isEqualTo(1);
    assertThat(diverged.missingCount()).isEqualTo(1);
  }

  private static VerificationReport compare(String source, String target) {
    return LiveSetComparator.compare(
        TableReaders.readQuietly(TableRef.parse(source), KEY),
        TableReaders.readQuietly(TableRef.parse(target), KEY));
  }

  private static Schema keySchema(Type keyType) {
    return new Schema(
        Types.NestedField.required(1, "id", Types.LongType.get()),
        Types.NestedField.required(2, "k", keyType));
  }

  private static List<Record> records(Schema schema, List<Object> keys) {
    List<Record> rows = new ArrayList<>();
    for (int i = 0; i < keys.size(); i++) {
      GenericRecord row = GenericRecord.create(schema);
      row.setField("id", (long) i);
      row.setField("k", keys.get(i));
      rows.add(row);
    }
    return rows;
  }

  private static void writeIceberg(
      Path location, String formatVersion, Schema schema, List<Object> keys) throws IOException {
    List<Record> rows = records(schema, keys);
    Table table =
        new HadoopTables(HadoopSupport.configuration())
            .create(
                schema,
                PartitionSpec.unpartitioned(),
                Map.of("format-version", formatVersion),
                HadoopSupport.uri(location));
    DataWriter<Record> writer =
        Parquet.writeData(
                table.io().newOutputFile(table.locationProvider().newDataLocation("a.parquet")))
            .schema(schema)
            .createWriterFunc(GenericParquetWriter::create)
            .withSpec(PartitionSpec.unpartitioned())
            .overwrite()
            .build();
    try (DataWriter<Record> closer = writer) {
      rows.forEach(closer::write);
    }
    table.newAppend().appendFile(writer.toDataFile()).commit();
  }

  private static void writeDelta(Path location, Schema schema, String keyType, List<Object> keys)
      throws IOException {
    List<Record> rows = records(schema, keys);
    Files.createDirectories(location.resolve("_delta_log"));
    Path dataFile = location.resolve("part-00000.parquet");
    FileAppender<Record> appender =
        Parquet.write(
                new HadoopFileIO(HadoopSupport.configuration())
                    .newOutputFile(HadoopSupport.uri(dataFile)))
            .schema(schema)
            .createWriterFunc(GenericParquetWriter::create)
            .overwrite()
            .build();
    try (FileAppender<Record> closer = appender) {
      rows.forEach(closer::add);
    }
    String deltaSchema =
        "{\"type\":\"struct\",\"fields\":["
            + "{\"name\":\"id\",\"type\":\"long\",\"nullable\":false,\"metadata\":{}},"
            + "{\"name\":\"k\",\"type\":\""
            + keyType
            + "\",\"nullable\":false,\"metadata\":{}}]}";
    String log =
        "{\"protocol\":{\"minReaderVersion\":3,\"minWriterVersion\":7,"
            + "\"readerFeatures\":[\"timestampNtz\"],\"writerFeatures\":[\"timestampNtz\"]}}\n"
            + "{\"metaData\":{\"id\":\""
            + UUID.randomUUID()
            + "\",\"format\":{\"provider\":\"parquet\",\"options\":{}},\"schemaString\":\""
            + deltaSchema.replace("\"", "\\\"")
            + "\",\"partitionColumns\":[],\"configuration\":{},\"createdTime\":0}}\n"
            + "{\"add\":{\"path\":\"part-00000.parquet\",\"partitionValues\":{},\"size\":"
            + Files.size(dataFile)
            + ",\"modificationTime\":0,\"dataChange\":true}}\n";
    Files.writeString(location.resolve("_delta_log").resolve(String.format("%020d.json", 0)), log);
  }
}
