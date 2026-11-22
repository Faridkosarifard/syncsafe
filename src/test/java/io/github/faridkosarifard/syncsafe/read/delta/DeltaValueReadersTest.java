package io.github.faridkosarifard.syncsafe.read.delta;

import static org.assertj.core.api.Assertions.assertThat;

import io.delta.kernel.data.ArrayValue;
import io.delta.kernel.data.ColumnVector;
import io.delta.kernel.data.MapValue;
import io.delta.kernel.types.BinaryType;
import io.delta.kernel.types.BooleanType;
import io.delta.kernel.types.ByteType;
import io.delta.kernel.types.DataType;
import io.delta.kernel.types.DateType;
import io.delta.kernel.types.DecimalType;
import io.delta.kernel.types.DoubleType;
import io.delta.kernel.types.FloatType;
import io.delta.kernel.types.IntegerType;
import io.delta.kernel.types.LongType;
import io.delta.kernel.types.ShortType;
import io.delta.kernel.types.StringType;
import io.delta.kernel.types.TimestampNTZType;
import io.delta.kernel.types.TimestampType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DeltaValueReadersTest {
  @Test
  void readsPrimitiveTypesAndNulls() {
    assertThat(DeltaValueReaders.read(vector(LongType.LONG, 9L), 0)).isEqualTo(9L);
    assertThat(DeltaValueReaders.read(vector(IntegerType.INTEGER, 4), 0)).isEqualTo(4L);
    assertThat(DeltaValueReaders.read(vector(DateType.DATE, 10), 0)).isEqualTo(10L);
    assertThat(DeltaValueReaders.read(vector(ShortType.SHORT, (short) 3), 0)).isEqualTo(3L);
    assertThat(DeltaValueReaders.read(vector(ByteType.BYTE, (byte) 2), 0)).isEqualTo(2L);
    assertThat(DeltaValueReaders.read(vector(StringType.STRING, "Ada"), 0)).isEqualTo("Ada");
    assertThat(DeltaValueReaders.read(vector(BooleanType.BOOLEAN, true), 0)).isEqualTo(true);
    assertThat(DeltaValueReaders.read(vector(DoubleType.DOUBLE, 1.5d), 0)).isEqualTo(1.5d);
    assertThat(DeltaValueReaders.read(vector(FloatType.FLOAT, 2.5f), 0)).isEqualTo(2.5d);
    assertThat(DeltaValueReaders.read(vector(DecimalType.USER_DEFAULT, new BigDecimal("3.14")), 0))
        .isEqualTo(new BigDecimal("3.14"));
    assertThat(DeltaValueReaders.read(vector(BinaryType.BINARY, new byte[] {1, 2}), 0))
        .isEqualTo(new byte[] {1, 2});
    assertThat(DeltaValueReaders.read(vector(TimestampType.TIMESTAMP, 99L), 0)).isEqualTo(99L);
    assertThat(DeltaValueReaders.read(vector(TimestampNTZType.TIMESTAMP_NTZ, 88L), 0))
        .isEqualTo(88L);
    assertThat(DeltaValueReaders.read(vector(StringType.STRING, null), 0)).isNull();
  }

  private static ColumnVector vector(DataType type, Object value) {
    return new ColumnVector() {
      @Override
      public DataType getDataType() {
        return type;
      }

      @Override
      public int getSize() {
        return 1;
      }

      @Override
      public void close() {}

      @Override
      public boolean isNullAt(int row) {
        return value == null;
      }

      @Override
      public boolean getBoolean(int row) {
        return (Boolean) value;
      }

      @Override
      public byte getByte(int row) {
        return (Byte) value;
      }

      @Override
      public short getShort(int row) {
        return (Short) value;
      }

      @Override
      public int getInt(int row) {
        return (Integer) value;
      }

      @Override
      public long getLong(int row) {
        return (Long) value;
      }

      @Override
      public float getFloat(int row) {
        return (Float) value;
      }

      @Override
      public double getDouble(int row) {
        return (Double) value;
      }

      @Override
      public byte[] getBinary(int row) {
        return (byte[]) value;
      }

      @Override
      public String getString(int row) {
        return (String) value;
      }

      @Override
      public BigDecimal getDecimal(int row) {
        return (BigDecimal) value;
      }

      @Override
      public MapValue getMap(int row) {
        return null;
      }

      @Override
      public ArrayValue getArray(int row) {
        return null;
      }

      @Override
      public ColumnVector getChild(int row) {
        return null;
      }
    };
  }
}
