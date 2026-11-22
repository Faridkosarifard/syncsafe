package io.github.faridkosarifard.syncsafe.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeletionVectorIOTest {
  @TempDir Path tmp;

  @Test
  void writesProtocolFramedPayload() throws Exception {
    Path file = tmp.resolve("deletion_vector.bin");
    DeletionVectorIO.Written written = DeletionVectorIO.write(file, List.of(0L, 2L, 5L));
    assertThat(written.offset()).isEqualTo(1);
    assertThat(written.cardinality()).isEqualTo(3);
    byte[] bytes = Files.readAllBytes(file);
    assertThat(bytes[0]).isEqualTo(DeletionVectorIO.FORMAT_VERSION);
    ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
    header.get();
    int size = header.getInt();
    assertThat(size).isEqualTo(written.sizeInBytes());
    byte[] payload = new byte[size];
    header.get(payload);
    int checksum = header.getInt();
    CRC32 crc = new CRC32();
    crc.update(payload);
    assertThat(checksum).isEqualTo((int) crc.getValue());
    ByteBuffer body = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
    assertThat(body.getInt()).isEqualTo(DeletionVectorIO.MAGIC);
    assertThat(body.getLong()).isEqualTo(1L);
  }
}
