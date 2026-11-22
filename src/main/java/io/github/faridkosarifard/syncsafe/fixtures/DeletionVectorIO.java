package io.github.faridkosarifard.syncsafe.fixtures;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.zip.CRC32;
import org.roaringbitmap.RoaringBitmap;

/**
 * Writes a Delta deletion-vector file as specified in the Delta protocol: a version byte, a
 * big-endian payload length, a little-endian portable roaring64 payload, and a big-endian CRC-32 of
 * the payload. The descriptor {@code offset} is 1 (immediately after the version byte).
 */
public final class DeletionVectorIO {
  public static final int MAGIC = 1681511377;
  public static final byte FORMAT_VERSION = 1;
  public static final int DEFAULT_OFFSET = 1;

  private DeletionVectorIO() {}

  public static Written write(Path file, Collection<Long> deletedPositions) throws IOException {
    RoaringBitmap[] buckets = new RoaringBitmap[] {new RoaringBitmap()};
    int maxKey = 0;
    for (Long position : deletedPositions) {
      int key = (int) (position >>> 32);
      int bit = (int) (position & 0xFFFFFFFFL);
      if (key > maxKey) {
        RoaringBitmap[] expanded = new RoaringBitmap[key + 1];
        System.arraycopy(buckets, 0, expanded, 0, buckets.length);
        buckets = expanded;
        maxKey = key;
      }
      if (buckets[key] == null) {
        buckets[key] = new RoaringBitmap();
      }
      buckets[key].add(bit);
    }
    int bitmapCount = 0;
    int serializedBitmaps = 0;
    for (int key = 0; key < buckets.length; key++) {
      RoaringBitmap bitmap = buckets[key];
      if (bitmap != null && !bitmap.isEmpty()) {
        bitmap.runOptimize();
        bitmapCount++;
        serializedBitmaps += Integer.BYTES + bitmap.serializedSizeInBytes();
      }
    }
    int payloadSize = Integer.BYTES + Long.BYTES + serializedBitmaps;
    ByteBuffer payload = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN);
    payload.putInt(MAGIC);
    payload.putLong(bitmapCount);
    for (int key = 0; key < buckets.length; key++) {
      RoaringBitmap bitmap = buckets[key];
      if (bitmap != null && !bitmap.isEmpty()) {
        payload.putInt(key);
        bitmap.serialize(payload);
      }
    }
    byte[] payloadBytes = payload.array();
    CRC32 crc = new CRC32();
    crc.update(payloadBytes, 0, payloadBytes.length);
    int checksum = (int) crc.getValue();

    ByteBuffer fileBytes = ByteBuffer.allocate(1 + Integer.BYTES + payloadSize + Integer.BYTES);
    fileBytes.order(ByteOrder.BIG_ENDIAN);
    fileBytes.put(FORMAT_VERSION);
    fileBytes.putInt(payloadSize);
    fileBytes.put(payloadBytes);
    fileBytes.putInt(checksum);
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.write(file, fileBytes.array());
    return new Written(DEFAULT_OFFSET, payloadSize, deletedPositions.size());
  }

  public record Written(int offset, int sizeInBytes, long cardinality) {}
}
