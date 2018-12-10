/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      Copyright H20.ai Limited
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package water.parser.parquet;

import org.apache.parquet.io.api.Binary;

import java.util.concurrent.TimeUnit;

/**
 * Class for decoding INT96 encoded parquet timestamp to timestamp millis in GMT.
 * <p>
 * This class is equivalent of @see org.apache.hadoop.hive.ql.io.parquet.timestamp.NanoTime,
 * which produces less intermediate objects during decoding.
 * 
 * This class is a modified version of ParquetTimestampUtils from Presto project.
 */
final class ParquetInt96TimestampConverter {
  private static final int JULIAN_EPOCH_OFFSET_DAYS = 2_440_588;
  private static final long MILLIS_IN_DAY = TimeUnit.DAYS.toMillis(1);
  private static final long NANOS_PER_MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1);
  private static final byte BYTES_IN_INT96_TIMESTAMP = 12;

  private ParquetInt96TimestampConverter() {
  }

  /**
   * Returns GMT timestamp from binary encoded parquet timestamp (12 bytes - julian date + time of day nanos).
   *
   * @param timestampBinary INT96 parquet timestamp
   * @return timestamp in millis, GMT timezone
   */
  public static long getTimestampMillis(Binary timestampBinary) {
    if (timestampBinary.length() != BYTES_IN_INT96_TIMESTAMP) {
      throw new IllegalArgumentException("Parquet timestamp must be 12 bytes long, actual " + timestampBinary.length());
    }
    byte[] bytes = timestampBinary.getBytes();

    // little endian encoding - bytes are red in inverted order
    long timeOfDayNanos = longFromBytes(bytes[7], bytes[6], bytes[5], bytes[4], bytes[3], bytes[2], bytes[1], bytes[0]);
    int julianDay = intFromBytes(bytes[11], bytes[10], bytes[9], bytes[8]);

    return julianDayToMillis(julianDay) + (timeOfDayNanos / NANOS_PER_MILLISECOND);
  }

  /**
   * @param julianDay Day since the beginning of Julian calendar
   * @return millis since epoch
   */
  private static long julianDayToMillis(int julianDay) {
    return (julianDay - JULIAN_EPOCH_OFFSET_DAYS) * MILLIS_IN_DAY;
  }


  /**
   * Returns the {@code long} value whose byte representation is the given 8 bytes, in big-endian
   * order; equivalent to {@code Longs.fromByteArray(new byte[] {b1, b2, b3, b4, b5, b6, b7, b8})}.
   *
   */
  public static long longFromBytes(
          byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
    return (b1 & 0xFFL) << 56
            | (b2 & 0xFFL) << 48
            | (b3 & 0xFFL) << 40
            | (b4 & 0xFFL) << 32
            | (b5 & 0xFFL) << 24
            | (b6 & 0xFFL) << 16
            | (b7 & 0xFFL) << 8
            | (b8 & 0xFFL);
  }

  /**
   * Returns the {@code int} value whose byte representation is the given 4 bytes, in big-endian
   * order; equivalent to {@code Ints.fromByteArray(new byte[] {b1, b2, b3, b4})}.
   */
  public static int intFromBytes(byte b1, byte b2, byte b3, byte b4) {
    return b1 << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
  }
}
