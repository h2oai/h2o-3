package water.util;

import static java.lang.Double.isNaN;

import java.util.concurrent.TimeUnit;

public class PrettyPrint {
  public static String msecs(long msecs, boolean truncate) {
    final long hr  = TimeUnit.MILLISECONDS.toHours  (msecs); msecs -= TimeUnit.HOURS  .toMillis(hr);
    final long min = TimeUnit.MILLISECONDS.toMinutes(msecs); msecs -= TimeUnit.MINUTES.toMillis(min);
    final long sec = TimeUnit.MILLISECONDS.toSeconds(msecs); msecs -= TimeUnit.SECONDS.toMillis(sec);
    final long ms  = TimeUnit.MILLISECONDS.toMillis (msecs);
    if( !truncate ) return String.format("%02d:%02d:%02d.%03d", hr, min, sec, ms);
    if( hr != 0 ) return String.format("%2d:%02d:%02d.%03d", hr, min, sec, ms);
    if( min != 0 ) return String.format("%2d min %2d.%03d sec", min, sec, ms);
    return String.format("%2d.%03d sec", sec, ms);
  }
  public static String usecs(long usecs) {
    final long hr = TimeUnit.MICROSECONDS.toHours (usecs); usecs -= TimeUnit.HOURS .toMicros(hr);
    final long min = TimeUnit.MICROSECONDS.toMinutes(usecs); usecs -= TimeUnit.MINUTES.toMicros(min);
    final long sec = TimeUnit.MICROSECONDS.toSeconds(usecs); usecs -= TimeUnit.SECONDS.toMicros(sec);
    final long ms = TimeUnit.MICROSECONDS.toMillis(usecs); usecs -= TimeUnit.MILLISECONDS.toMicros(ms);
    if( hr != 0 ) return String.format("%2d:%02d:%02d.%03d", hr, min, sec, ms);
    if( min != 0 ) return String.format("%2d min %2d.%03d sec", min, sec, ms);
    if( sec != 0 ) return String.format("%2d.%03d sec", sec, ms);
    if( ms != 0 ) return String.format("%3d.%03d msec", ms, usecs);
    return String.format("%3d usec", usecs);
  }

  // Return X such that (bytes < 1L<<(X*10))
  static int byteScale(long bytes) {
    for( int i=0; i<6; i++ )
      if( bytes < 1L<<(i*10) )
        return i;
    return 6;
  }
  static double bytesScaled(long bytes, int scale) {
    if( scale == 0 ) return bytes;
    return bytes / (double)(1L<<((scale-1)*10));
  }
  static final String[] SCALE = new String[] {"N/A","%4.0f  B","%.1f KB","%.1f MB","%.2f GB","%.3f TB","%.3f PB"};
  public static String bytes(long bytes) { return bytes(bytes,byteScale(bytes)); }
  static String bytes(long bytes, int scale) { return String.format(SCALE[scale],bytesScaled(bytes,scale)); }
  public static String bytesPerSecond(long bytes) {
    if( bytes < 0 ) return "N/A";
    return bytes(bytes)+"/S";
  }

  static double [] powers10 = new double[]{
    0.0000000001,
    0.000000001,
    0.00000001,
    0.0000001,
    0.000001,
    0.00001,
    0.0001,
    0.001,
    0.01,
    0.1,
    1.0,
    10.0,
    100.0,
    1000.0,
    10000.0,
    100000.0,
    1000000.0,
    10000000.0,
    100000000.0,
    1000000000.0,
    10000000000.0,
  };

  static public long [] powers10i = new long[]{
    1l,
    10l,
    100l,
    1000l,
    10000l,
    100000l,
    1000000l,
    10000000l,
    100000000l,
    1000000000l,
    10000000000l,
    100000000000l,
    1000000000000l,
    10000000000000l,
    100000000000000l,
    1000000000000000l,
    10000000000000000l,
    100000000000000000l,
    1000000000000000000l,
  };

  public static double pow10(int exp){ return ((exp >= -10 && exp <= 10)?powers10[exp+10]:Math.pow(10, exp)); }
  public static long pow10i(int exp){ return powers10i[exp]; }
  public static final boolean fitsIntoInt(double d) { return Math.abs((int)d - d) < 1e-8; }


  // About as clumsy and random as a blaster...
  public static String UUID( long lo, long hi ) {
    long lo0 = (lo>>32)&0xFFFFFFFFL;
    long lo1 = (lo>>16)&0xFFFFL;
    long lo2 = (lo>> 0)&0xFFFFL;
    long hi0 = (hi>>48)&0xFFFFL;
    long hi1 = (hi>> 0)&0xFFFFFFFFFFFFL;
    return String.format("%08X-%04X-%04X-%04X-%012X",lo0,lo1,lo2,hi0,hi1);
  }

  public static String formatPct(double pct) {
    String s = "N/A";
    if( !isNaN(pct) )
      s = String.format("%5.2f %%", 100 * pct);
    return s;
  }

  /**
   * This method takes a number, and returns the
   * string form of the number with the proper
   * ordinal indicator attached (e.g. 1->1st, and 22->22nd)
   * @param i - number to have ordinal indicator attached
   * @return string form of number along with ordinal indicator as a suffix
   */
  public static String withOrdinalIndicator(long i) {
    String ord;
    // Grab second to last digit
    int d = (int) (Math.abs(i) / Math.pow(10, 1)) % 10;
    if (d == 1) ord = "th"; //teen values all end in "th"
    else { // not a weird teen number
      d = (int) (Math.abs(i) / Math.pow(10, 0)) % 10;
      switch (d) {
        case 1: ord = "st"; break;
        case 2: ord = "st"; break;
        case 3: ord = "st"; break;
        default: ord = "th";
      }
    }
    return i+ord;
  }
}
