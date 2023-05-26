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
    long timeOfDayNanos = TypeUtils.longFromBytes(bytes[7], bytes[6], bytes[5], bytes[4], bytes[3], bytes[2], bytes[1], bytes[0]);
    int julianDay = TypeUtils.intFromBytes(bytes[11], bytes[10], bytes[9], bytes[8]);

    return julianDayToMillis(julianDay) + (timeOfDayNanos / NANOS_PER_MILLISECOND);
  }

  /**
   * @param julianDay Day since the beginning of Julian calendar
   * @return millis since epoch
   */
  private static long julianDayToMillis(int julianDay) {
    return (julianDay - JULIAN_EPOCH_OFFSET_DAYS) * MILLIS_IN_DAY;
  }



}
