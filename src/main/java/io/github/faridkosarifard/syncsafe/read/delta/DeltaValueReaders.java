package io.github.faridkosarifard.syncsafe.read.delta;

import io.delta.kernel.data.ColumnVector;
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

/** Extracts a Java value from a Kernel column vector for key comparison. */
final class DeltaValueReaders {
  private DeltaValueReaders() {}

  static Object read(ColumnVector vector, int row) {
    if (vector.isNullAt(row)) {
      return null;
    }
    DataType type = vector.getDataType();
    if (type instanceof LongType) {
      return vector.getLong(row);
    }
    if (type instanceof IntegerType || type instanceof DateType) {
      return (long) vector.getInt(row);
    }
    if (type instanceof ShortType) {
      return (long) vector.getShort(row);
    }
    if (type instanceof ByteType) {
      return (long) vector.getByte(row);
    }
    if (type instanceof StringType) {
      return vector.getString(row);
    }
    if (type instanceof BooleanType) {
      return vector.getBoolean(row);
    }
    if (type instanceof DoubleType) {
      return vector.getDouble(row);
    }
    if (type instanceof FloatType) {
      return (double) vector.getFloat(row);
    }
    if (type instanceof DecimalType) {
      return vector.getDecimal(row);
    }
    if (type instanceof BinaryType) {
      return vector.getBinary(row);
    }
    if (type instanceof TimestampType || type instanceof TimestampNTZType) {
      return vector.getLong(row);
    }
    return vector.getString(row);
  }
}
