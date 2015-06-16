package water.parser;

import water.fvec.C16Chunk;

/**
 * Utility class for parsing UUIDs.
 *
 * This class creates a hash value of two longs from
 * a {@link ValueString} containing a correct UUID.
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
  public static final boolean isUUID(ValueString str) {
    boolean res;
    int old = str.getOffset();
    attemptUUIDParseLow(str);
    attemptUUIDParseHigh(str);
    res = str.getOffset() != -1;
    str.setOff(old);
    return res;
  }

  /**
   * Attempts to parse the provided {@link ValueString} as
   * a UUID into hash value in two longs.
   *
   * Warning: as written, this method does modify the state
   * of the passed in ValueString.
   *
   * @param str
   * @return A two value long array array containing the low
   * and high hash values in indices 0,1 respectively.  For
   * invalid UUID strings, the returned values are
   * {link @C16Chunk._LO_NA} and {link @C16Chunk._HI_NA},
   * respectively.
   */
  public static long[] attemptUUIDParse( ValueString str) {
    long[] uuid = new long[2];
    uuid[0] = attemptUUIDParseLow(str);
    if (str.getOffset() == -1) return badUUID();
    uuid[1] = attemptUUIDParseHigh(str);
    if (str.getOffset() == -1) return badUUID();
    return uuid;
  }

  // --------------------------------
  // Parse XXXXXXXX-XXXX-XXXX and return an arbitrary long, or set str.off==-1
  // (and return Long.MIN_VALUE but this is a valid long return value).
  private static long attemptUUIDParseLow(ValueString str) {
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
  // (and return Long.MIN_VALUE but this is a valid long return value).
  public static long attemptUUIDParseHigh(ValueString str) {
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

  private static long attemptUUIDParseEnd(ValueString str, long lo, byte[] buf, int i) {
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
            (i+2< buf.length && hdigit(0,buf[i+2]) != Long.MIN_VALUE)) ? markBad(str) : lo;
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
  private static long markBad(ValueString str) {
    str.setOff(-1);
    return Long.MIN_VALUE;
  }
  private static long[] badUUID() {
    return new long[]{C16Chunk._LO_NA, C16Chunk._HI_NA};
  }
}
