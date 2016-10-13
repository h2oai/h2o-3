package water.parser;

import water.fvec.C16Chunk;

import java.util.UUID;

/**
 * Utility class for parsing UUIDs.
 *
 * This class creates a hash value of two longs from
 * a {@link BufferedString} containing a correct UUID.
 *
 */
public class ParseUUID {
  /**
   * Confirms whether the provided UUID is considered
   * valid.
   *
   * @param str
   * @return TRUE if str represents a valid UUID
   */
  public static boolean isUUID(BufferedString str) {
    boolean res;
    int old = str.getOffset();
    attemptUUIDParseLow(str);
    attemptUUIDParseHigh(str);
    res = str.getOffset() != -1;
    str.setOff(old);
    return res;
  }

  /**
   * Attempts to parse the provided {@link BufferedString} as
   * a UUID into hash value in two longs.
   *
   * Warning: as written, this method does modify the state
   * of the passed in BufferedString.
   *
   * @param str
   * @return A parsed UUID, or a null if parsing failed.
   */
  public static UUID attemptUUIDParse(BufferedString str) {
    Long lo = attemptUUIDParseLow(str);
    Long hi = attemptUUIDParseHigh(str);
    return (str.getOffset() == -1) ? null : buildUUID(lo, hi);
  }

  private static UUID buildUUID(Long lo, Long hi) {
    return (lo == null || hi == null || (C16Chunk.isNA(lo, hi))) ? null : new UUID(hi, lo);
  }

  // --------------------------------
  // Parse XXXXXXXX-XXXX-XXXX and return an arbitrary long, or set str.off==-1
  // (and return Long.MIN_VALUE but this is a valid long return value).
  private static Long attemptUUIDParseLow(BufferedString str) {
    final byte[] buf = str.getBuffer();
    int i=str.getOffset();
    if( i+36 > buf.length ) return markBad(str);
    long lo=0;
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    if( buf[i++]!='-' ) return markBad(str);
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    if( buf[i++]!='-' ) return markBad(str);
    lo = get2(lo,buf,(i+=2)-2);
    return attemptUUIDParseEnd(str, lo, buf, i);
  }

  // Parse -XXXX-XXXXXXXXXXXX and return an arbitrary long, or set str.off==-1
  // (and return null).
  public static Long attemptUUIDParseHigh(BufferedString str) {
    final byte[] buf = str.getBuffer();
    int i=str.getOffset();
    if ( i== -1 ) return markBad(str);
    long hi=0;
    if( buf[i++]!='-' ) return markBad(str);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    if( buf[i++]!='-' ) return markBad(str);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    return attemptUUIDParseEnd(str, hi, buf, i);
  }

  private static Long attemptUUIDParseEnd(BufferedString str, long lo, byte[] buf, int i) {
    // Can never equal MIN_VALUE since only parsed 14 of 16 digits, unless
    // failed parse already.
    if( lo == Long.MIN_VALUE ) return markBad(str);
    // If the last 2 digits are 0x8000 and the first 14 are all 0's then might
    // legitimately parse MIN_VALUE, need to check for it special.
    str.setOff(i+2);            // Mark as parsed
    if( lo == 0x80000000000000L && buf[i]=='0' && buf[i+1]=='0' )
      return Long.MIN_VALUE;    // Valid MIN_VALUE parse
    // First 14 digits are a random scramble; will never equal MIN_VALUE result
    // unless we have a failed parse in the last 2 digits
    lo = get2(lo,buf,i);
    return (lo == Long.MIN_VALUE || // broken UUID already, OR
        // too many valid UUID digits
        (i + 2 < buf.length && hdigit(0, buf[i + 2]) != Long.MIN_VALUE)) ? null : lo;
  }


  private static long get2( long x, byte[] buf, int i ) {
    if( x == Long.MIN_VALUE ) return x;
    x = hdigit(x,buf[i++]);
    x = hdigit(x,buf[i++]);
    return x;
  }

  private static long hdigit( long x, byte b ) {
    if( x == Long.MIN_VALUE ) return Long.MIN_VALUE;
    else if( b >= '0' && b <= '9' ) return (x<<4)+b-'0';
    else if( b >= 'A' && b <= 'F' ) return (x<<4)+b-'A'+10;
    else if( b >= 'a' && b <= 'f' ) return (x<<4)+b-'a'+10;
    else return Long.MIN_VALUE;
  }
  private static Long markBad(BufferedString str) {
    str.setOff(-1);
    return null;
  }
}
