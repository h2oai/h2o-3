package water.parser.parquet.ext;

import java.nio.ByteBuffer;
import java.math.BigInteger;
import java.math.BigDecimal;
import static java.lang.Math.pow;

import org.apache.parquet.io.api.Binary;

/*
 *
 * Note: this code 1-1 copy of https://github.com/apache/parquet-mr/blob/master/parquet-pig/src/main/java/org/apache/parquet/pig/convert/DecimalUtils.java
 * All credit goes to original Parquet contributors
 * 
 * Conversion between Parquet Decimal Type to Java BigDecimal in Pig
 * Code Based on the Apache Spark ParquetRowConverter.scala
 */
public class DecimalUtils {

  public static BigDecimal binaryToDecimal(Binary value, int precision, int scale) {
    /*
     * Precision <= 18 checks for the max number of digits for an unscaled long,
     * else treat with big integer conversion
     */
    if (precision <= 18) {
      ByteBuffer buffer = value.toByteBuffer();
      byte[] bytes = buffer.array();
      int start = buffer.arrayOffset() + buffer.position();
      int end = buffer.arrayOffset() + buffer.limit();
      long unscaled = 0L;
      int i = start;
      while ( i < end ) {
        unscaled = ( unscaled << 8 | bytes[i] & 0xff );
        i++;
      }
      int bits = 8*(end - start);
      long unscaledNew = (unscaled << (64 - bits)) >> (64 - bits);
      if (unscaledNew <= -pow(10,18) || unscaledNew >= pow(10,18)) {
        return new BigDecimal(unscaledNew);
      } else {
        return BigDecimal.valueOf(unscaledNew / pow(10,scale));
      }
    } else {
      return new BigDecimal(new BigInteger(value.getBytes()), scale);
    }
  }
}
