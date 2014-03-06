package water.util;

import java.util.concurrent.TimeUnit;

public class PrettyPrint {
  public static String msecs(long msecs, boolean truncate) {
    final long hr = TimeUnit.MILLISECONDS.toHours (msecs); msecs -= TimeUnit.HOURS .toMillis(hr);
    final long min = TimeUnit.MILLISECONDS.toMinutes(msecs); msecs -= TimeUnit.MINUTES.toMillis(min);
    final long sec = TimeUnit.MILLISECONDS.toSeconds(msecs); msecs -= TimeUnit.SECONDS.toMillis(sec);
    final long ms = TimeUnit.MILLISECONDS.toMillis (msecs);
    if( !truncate ) return String.format("%02d:%02d:%02d.%03d", hr, min, sec, ms);
    if( hr != 0 ) return String.format("%2d:%02d:%02d.%03d", hr, min, sec, ms);
    if( min != 0 ) return String.format("%2d min %2d.%03d sec", min, sec, ms);
    return String.format("%2d.%03d sec", sec, ms);
  }

  // Return X such that (bytes < 1L<<(X*10))
  public static int byteScale(long bytes) {
    for( int i=0; i<6; i++ )
      if( bytes < 1L<<(i*10) )
        return i;
    return 6;
  }
  public static double bytesScaled(long bytes, int scale) {
    if( scale == 0 ) return bytes;
    return bytes / (double)(1L<<((scale-1)*10));
  }
  public static final String[] SCALE = new String[] {"N/A","%3.0f B ","%.1f KB","%.1f MB","%.2f GB","%.3f TB","%.3f PB"};
  public static String bytes(long bytes) { return bytes(bytes,byteScale(bytes)); }
  public static String bytes(long bytes, int scale) { return String.format(SCALE[scale],bytesScaled(bytes,scale)); }
  public static String bytesPerSecond(long bytes) {
    if( bytes < 0 ) return "N/A";
    return bytes(bytes)+"/S";
  }
}
