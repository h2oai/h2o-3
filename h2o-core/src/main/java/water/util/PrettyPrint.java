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

  public static String[] createConfusionMatrixHeader( long xs[], String ds[] ) {
    String ss[] = new String[xs.length]; // the same length
    for( int i=0; i<ds.length; i++ )
      if( xs[i] >= 0 || (ds[i] != null && ds[i].length() > 0) && !Integer.toString(i).equals(ds[i]) )
        ss[i] = ds[i];
    if( ds.length == xs.length-1 && xs[xs.length-1] > 0 )
      ss[xs.length-1] = "NA";
    return ss;
  }

  public static StringBuilder printConfusionMatrix(StringBuilder sb, long[][] cm, String[] domain, boolean html) {
    if (cm == null || domain == null) return sb;
    for (int i=0; i<cm.length; ++i) assert(cm.length == cm[i].length);
//    if (html) DocGen.HTML.arrayHead(sb);
    // Sum up predicted & actuals
    long acts [] = new long[cm   .length];
    long preds[] = new long[cm[0].length];
    for( int a=0; a<cm.length; a++ ) {
      long sum=0;
      for( int p=0; p<cm[a].length; p++ ) {
        sum += cm[a][p];
        preds[p] += cm[a][p];
      }
      acts[a] = sum;
    }
    String adomain[] = createConfusionMatrixHeader(acts , domain);
    String pdomain[] = createConfusionMatrixHeader(preds, domain);
    assert adomain.length == pdomain.length : "The confusion matrix should have the same length for both directions.";

    String fmt = "";
    String fmtS = "";

    // Header
    if (html) {
      sb.append("<tr class='warning' style='min-width:60px'>");
      sb.append("<th>&darr; Actual / Predicted &rarr;</th>");
      for( int p=0; p<pdomain.length; p++ )
        if( pdomain[p] != null )
          sb.append("<th style='min-width:60px'>").append(pdomain[p]).append("</th>");
      sb.append("<th>Error</th>");
      sb.append("</tr>");
    } else {
      // determine max length of each space-padded field
      int maxlen = 0;
      for( String s : pdomain ) if( s != null ) maxlen = Math.max(maxlen, s.length());
      long lsum = 0;
      for( int a=0; a<cm.length; a++ ) {
        if( adomain[a] == null ) continue;
        for( int p=0; p<pdomain.length; p++ ) { if( pdomain[p] == null ) continue; lsum += cm[a][p]; }
      }
      maxlen = Math.max(8, Math.max(maxlen, String.valueOf(lsum).length()) + 2);
      fmt  = "%" + maxlen + "d";
      fmtS = "%" + maxlen + "s";
      sb.append(String.format(fmtS, "Act/Prd"));
      for( String s : pdomain ) if( s != null ) sb.append(String.format(fmtS, s));
      sb.append("   " + String.format(fmtS, "Error\n"));
    }

    // Main CM Body
    long terr=0;
    for( int a=0; a<cm.length; a++ ) {
      if( adomain[a] == null ) continue;
      if (html) {
        sb.append("<tr style='min-width:60px'>");
        sb.append("<th style='min-width:60px'>").append(adomain[a]).append("</th>");
      } else {
        sb.append(String.format(fmtS,adomain[a]));
      }
      long correct=0;
      for( int p=0; p<pdomain.length; p++ ) {
        if( pdomain[p] == null ) continue;
        boolean onDiag = adomain[a].equals(pdomain[p]);
        if( onDiag ) correct = cm[a][p];
        String id = "";
        if (html) {
          sb.append(onDiag ? "<td style='min-width: 60px; background-color:LightGreen' "+id+">":"<td style='min-width: 60px;'"+id+">").append(String.format("%,d", cm[a][p])).append("</td>");
        } else {
          sb.append(String.format(fmt,cm[a][p]));
        }
      }
      long err = acts[a]-correct;
      terr += err;
      if (html) {
        sb.append(String.format("<th  style='min-width: 60px;'>%.05f = %,d / %,d</th></tr>", (double)err/acts[a], err, acts[a]));
      } else {
        sb.append("   " + String.format("%.05f = %,d / %d\n", (double)err/acts[a], err, acts[a]));
      }
    }

    // Last row of CM
    if (html) {
      sb.append("<tr style='min-width:60px'><th>Totals</th>");
    } else {
      sb.append(String.format(fmtS, "Totals"));
    }
    for( int p=0; p<pdomain.length; p++ ) {
      if( pdomain[p] == null ) continue;
      if (html) {
        sb.append("<td style='min-width:60px'>").append(String.format("%,d", preds[p])).append("</td>");
      } else {
        sb.append(String.format(fmt, preds[p]));
      }
    }
    long nrows = 0;
    for (long n : acts) nrows += n;

    if (html) {
      sb.append(String.format("<th style='min-width:60px'>%.05f = %,d / %,d</th></tr>", (float)terr/nrows, terr, nrows));
//      DocGen.HTML.arrayTail(sb);
    } else {
      sb.append("   " + String.format("%.05f = %,d / %,d\n", (float)terr/nrows, terr, nrows));
    }
    return sb;                  // For flow-coding
  }
}
