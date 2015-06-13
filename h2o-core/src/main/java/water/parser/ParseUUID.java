package water.parser;

public class ParseUUID {
  public static final boolean isUUID(ValueString str) {
    boolean res;
    int old = str.get_off();
    attemptUUIDParse0(str);
    attemptUUIDParse1(str);
    res = str.get_off() != -1;
    str.setOff(old);
    return res;
  }
  // --------------------------------
  // Parse XXXXXXXX-XXXX-XXXX and return an arbitrary long, or set str.off==-1
  // (and return Long.MIN_VALUE but this is a valid long return value).
  public static long attemptUUIDParse0( ValueString str ) {
    final byte[] buf = str.get_buf();
    int i=str.get_off();
    if( i+36>buf.length ) return badUUID(str);
    long lo=0;
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    if( buf[i++]!='-' ) return badUUID(str);
    lo = get2(lo,buf,(i+=2)-2);
    lo = get2(lo,buf,(i+=2)-2);
    if( buf[i++]!='-' ) return badUUID(str);
    lo = get2(lo,buf,(i+=2)-2);
    return attemptUUIDParseLast(str,lo,buf,i);
  }
  // Parse -XXXX-XXXXXXXXXXXX and return an arbitrary long, or set str.off==-1
  // (and return Long.MIN_VALUE but this is a valid long return value).
  public static long attemptUUIDParse1( ValueString str ) {
    final byte[] buf = str.get_buf();
    int i=str.get_off();
    if( i== -1 ) return badUUID(str);
    long hi=0;
    if( buf[i++]!='-' ) return badUUID(str);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    if( buf[i++]!='-' ) return badUUID(str);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    hi = get2(hi,buf,(i+=2)-2);
    return attemptUUIDParseLast(str,hi,buf,i);
  }

  private static long attemptUUIDParseLast( ValueString str, long lo, byte[] buf, int i ) {
    // Can never equal MIN_VALUE since only parsed 14 of 16 digits, unless
    // failed parse already.
    if( lo == Long.MIN_VALUE ) return badUUID(str);
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
            (i+2< buf.length && hdigit(0,buf[i+2]) != Long.MIN_VALUE)) ? badUUID(str) : lo;
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
  public static long badUUID( ValueString str ) {
    str.setOff(-1);
    return Long.MIN_VALUE;
  }
}
