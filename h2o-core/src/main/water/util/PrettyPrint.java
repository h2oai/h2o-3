package water.util;

import java.util.concurrent.TimeUnit;

public class PrettyPrint {
  static String msecs(long msecs, boolean truncate) {
    final long hr  = TimeUnit.MILLISECONDS.toHours  (msecs); msecs -= TimeUnit.HOURS  .toMillis(hr);
    final long min = TimeUnit.MILLISECONDS.toMinutes(msecs); msecs -= TimeUnit.MINUTES.toMillis(min);
    final long sec = TimeUnit.MILLISECONDS.toSeconds(msecs); msecs -= TimeUnit.SECONDS.toMillis(sec);
    final long ms  = TimeUnit.MILLISECONDS.toMillis (msecs);
    if( !truncate ) return String.format("%02d:%02d:%02d.%03d", hr, min, sec, ms);
    if( hr != 0 ) return String.format("%2d:%02d:%02d.%03d", hr, min, sec, ms);
    if( min != 0 ) return String.format("%2d min %2d.%03d sec", min, sec, ms);
    return String.format("%2d.%03d sec", sec, ms);
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
  static final String[] SCALE = new String[] {"N/A","%3.0f B ","%.1f KB","%.1f MB","%.2f GB","%.3f TB","%.3f PB"};
  public static String bytes(long bytes) { return bytes(bytes,byteScale(bytes)); }
  static String bytes(long bytes, int scale) { return String.format(SCALE[scale],bytesScaled(bytes,scale)); }
  static String bytesPerSecond(long bytes) {
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
}
