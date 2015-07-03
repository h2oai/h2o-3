package water.parser;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import water.util.Log;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public abstract class ParseTime {
  // Deduce if we are looking at a Date/Time value, or not.
  // If so, return time as msec since Jan 1, 1970 or Long.MIN_VALUE.

  // I tried java.util.SimpleDateFormat, but it just throws too many
  // exceptions, including ParseException, NumberFormatException, and
  // ArrayIndexOutOfBoundsException... and the Piece de resistance: a
  // ClassCastException deep in the SimpleDateFormat code:
  // "sun.util.calendar.Gregorian$Date cannot be cast to sun.util.calendar.JulianCalendar$Date"

  public static final boolean isTime(ValueString str) {
    return attemptTimeParse(str) != Long.MIN_VALUE;
  }

  private static int digit( int x, int c ) {
    if( x < 0 || c < '0' || c > '9' ) return -1;
    return x*10+(c-'0');
  }

  // So I just brutally parse "dd-MMM-yy".
  private static final byte MMS[][][] = new byte[][][] {
    {"jan".getBytes(),"january"  .getBytes()},
    {"feb".getBytes(),"february" .getBytes()},
    {"mar".getBytes(),"march"    .getBytes()},
    {"apr".getBytes(),"april"    .getBytes()},
    {"may".getBytes(),"may"      .getBytes()},
    {"jun".getBytes(),"june"     .getBytes()},
    {"jul".getBytes(),"july"     .getBytes()},
    {"aug".getBytes(),"august"   .getBytes()},
    {"sep".getBytes(),"september".getBytes()},
    {"oct".getBytes(),"october"  .getBytes()},
    {"nov".getBytes(),"november" .getBytes()},
    {"dec".getBytes(),"december" .getBytes()}
  };
  
  // Time parse patterns
  public static final String TIME_PARSE[] = { "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss.SSS", "dd-MMM-yy" };

  // Returns:
  //  - not a time parse: Long.MIN_VALUE 
  //  - time parse via pattern X: time in msecs since Jan 1, 1970, shifted left by 1 byte, OR'd with X
  static long encodeTimePat(long tcode, int tpat ) { return (tcode<<8)|tpat; }
  static long decodeTime(long tcode ) { return tcode>>8; }
  static int  decodePat (long tcode ) { return ((int)tcode&0xFF); }
  public static long attemptTimeParse( ValueString str ) {
    try {
      long t0 = attemptTimeParse_01(str); // "yyyy-MM-dd" and that plus " HH:mm:ss.SSS"
      if( t0 != Long.MIN_VALUE ) return t0;
      long t2 = attemptTimeParse_2 (str); // "dd-MMM-yy"
      if( t2 != Long.MIN_VALUE ) return t2;
    } catch( org.joda.time.IllegalFieldValueException | // Not time at all
             org.joda.time.IllegalInstantException      // Parsed as time, but falls into e.g. a daylight-savings hour hole
             ie ) { }
    return Long.MIN_VALUE;
  }
  // So I just brutally parse "yyyy-MM-dd HH:mm:ss.SSS"
  private static long attemptTimeParse_01( ValueString str ) {
    final byte[] buf = str.getBuffer();
    int i=str.getOffset();
    final int end = i+str.length();
    while( i < end && buf[i] == ' ' ) i++;
    if   ( i < end && buf[i] == '"' ) i++;
    if( (end-i) != 10 && (end-i) < 19 ) return Long.MIN_VALUE;
    int yy=0, MM=0, dd=0, HH=0, mm=0, ss=0, SS=0;
    yy = digit(yy,buf[i++]);
    yy = digit(yy,buf[i++]);
    yy = digit(yy,buf[i++]);
    yy = digit(yy,buf[i++]);
    if( buf[i++] != '-' ) return Long.MIN_VALUE;
    MM = digit(MM,buf[i++]);
    MM = digit(MM,buf[i++]);
    if( MM < 1 || MM > 12 ) return Long.MIN_VALUE;
    if( buf[i++] != '-' ) return Long.MIN_VALUE;
    dd = digit(dd,buf[i++]);
    dd = digit(dd,buf[i++]);
    if( dd < 1 || dd > 31 ) return Long.MIN_VALUE;
    if( i==end )
      return encodeTimePat(new DateTime(yy,MM,dd,0,0,0, getTimezone()).getMillis(),0);
    if( buf[i++] != ' ' ) return Long.MIN_VALUE;
    HH = digit(HH,buf[i++]);
    HH = digit(HH,buf[i++]);
    if( HH < 0 || HH > 23 ) return Long.MIN_VALUE;
    if( buf[i++] != ':' ) return Long.MIN_VALUE;
    mm = digit(mm,buf[i++]);
    mm = digit(mm,buf[i++]);
    if( mm < 0 || mm > 59 ) return Long.MIN_VALUE;
    if( buf[i++] != ':' ) return Long.MIN_VALUE;
    ss = digit(ss,buf[i++]);
    ss = digit(ss,buf[i++]);
    if( ss < 0 || ss > 59 ) return Long.MIN_VALUE;
    if( i<end && buf[i] == '.' ) {
      i++;
      if( i<end ) SS = digit(SS,buf[i++]);
      if( i<end ) SS = digit(SS,buf[i++]);
      if( i<end ) SS = digit(SS,buf[i++]);
      if( SS < 0 || SS > 999 ) return Long.MIN_VALUE;
    }
    if( i<end && buf[i] == '"' ) i++;
    if( i<end ) return Long.MIN_VALUE;
    return encodeTimePat(new DateTime(yy,MM,dd,HH,mm,ss,getTimezone()).getMillis()+SS,1);
  }
  private static DateTimeZone _timezone;

  public static void setTimezone(String tz) {
    Set<String> idSet = DateTimeZone.getAvailableIDs();
    if(idSet.contains(tz))
      _timezone = DateTimeZone.forID(tz);
    else
      Log.err("Attempted to set unrecognized timezone: "+ tz);
  }

  public static DateTimeZone getTimezone() {
    return _timezone == null ? DateTimeZone.getDefault() : _timezone;
  }

  public static String listTimezones() {
    DateTimeFormatter offsetFormatter = new DateTimeFormatterBuilder().appendTimeZoneOffset(null, true, 2, 4).toFormatter();
    Set<String> idSet = DateTimeZone.getAvailableIDs();
    Map<String, String> tzMap = new TreeMap();
    Iterator<String> it = idSet.iterator();
    String id, cid, offset, key, output;
    DateTimeZone tz;
    int i = 0;
    long millis = System.currentTimeMillis();


    // collect canonical and alias IDs into a map
    while (it.hasNext()) {
      id = it.next();
      tz = DateTimeZone.forID(id);
      cid = tz.getID();
      offset = offsetFormatter.withZone(tz).print(tz.getStandardOffset(millis));
      key = offset + " " + cid;
      if (id == cid) { // Canonical ID
        if (!tzMap.containsKey(key)) tzMap.put(key, "");
      }  else {// alias ID
        if (!tzMap.containsKey(key))
          tzMap.put(key, "");
        tzMap.put(key,  tzMap.get(key) + ", " + id);
      }
    }

    // assemble result
    output = "StandardOffset CanonicalID, Aliases\n";
    for (Map.Entry<String, String> e : tzMap.entrySet())
      output += e.getKey() + e.getValue()+"\n";

    return output;
  }

  /**
   * Factory to create a formatter from a strptime pattern string.
   * This models the commonly supported features of strftime from POSIX
   * (where it can).
   * <p>
   * The format may contain locale specific output, and this will change as
   * you change the locale of the formatter.
   * Call DateTimeFormatter.withLocale(Locale) to switch the locale.
   * For example:
   * <pre>
   * DateTimeFormat.forPattern(pattern).withLocale(Locale.FRANCE).print(dt);
   * </pre>
   *
   * @param pattern  pattern specification
   * @return the formatter
   *  @throws IllegalArgumentException if the pattern is invalid
   */
  public static DateTimeFormatter forStrptimePattern(String pattern) {
    if (pattern == null || pattern.length() == 0)
      throw new IllegalArgumentException("Empty date time pattern specification");

    DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
    parseToBuilder(builder, pattern);
    DateTimeFormatter formatter = builder.toFormatter();

    return formatter;
  }

  /**
   * Parses the given pattern and appends the rules to the given
   * DateTimeFormatterBuilder. See strptime man page for valid patterns.
   *
   * @param pattern  pattern specification
   * @throws IllegalArgumentException if the pattern is invalid
   */
  private static void parseToBuilder(DateTimeFormatterBuilder builder, String pattern) {
    int length = pattern.length();
    int[] indexRef = new int[1];

    for (int i=0; i<length; i++) {
      indexRef[0] = i;
      String token = parseToken(pattern, indexRef);
      i = indexRef[0];

      int tokenLen = token.length();
      if (tokenLen == 0) {
        break;
      }
      char c = token.charAt(0);

      if (c == '%' && token.charAt(1) != '%') {
        c = token.charAt(1);
        switch(c) {
          case 'a':
            builder.appendDayOfWeekShortText();
            break;
          case 'A':
            builder.appendDayOfWeekText();
            break;
          case 'b':
          case 'h':
            builder.appendMonthOfYearShortText();
            break;
          case 'B':
            builder.appendMonthOfYearText();
            break;
          case 'c':
            builder.appendDayOfWeekShortText();
            builder.appendLiteral(' ');
            builder.appendMonthOfYearShortText();
            builder.appendLiteral(' ');
            builder.appendDayOfMonth(2);
            builder.appendLiteral(' ');
            builder.appendHourOfDay(2);
            builder.appendLiteral(':');
            builder.appendMinuteOfHour(2);
            builder.appendLiteral(':');
            builder.appendSecondOfMinute(2);
            builder.appendLiteral(' ');
            builder.appendYear(4,4);
            break;
          case 'C':
            builder.appendCenturyOfEra(1,2);
            break;
          case 'd':
            builder.appendDayOfMonth(2);
            break;
          case 'D':
            builder.appendMonthOfYear(2);
            builder.appendLiteral('/');
            builder.appendDayOfMonth(2);
            builder.appendLiteral('/');
            builder.appendTwoDigitYear(2019);
            break;
          case 'e':
            builder.appendOptional(DateTimeFormat.forPattern("' '").getParser());
            builder.appendDayOfMonth(2);
            break;
          case 'F':
            builder.appendYear(4,4);
            builder.appendLiteral('-');
            builder.appendMonthOfYear(2);
            builder.appendLiteral('-');
            builder.appendDayOfMonth(2);
            break;
          case 'g':
          case 'G':
            break; //for output only, accepted and ignored for input
          case 'H':
            builder.appendHourOfDay(2);
            break;
          case 'I':
            builder.appendClockhourOfHalfday(2);
            break;
          case 'j':
            builder.appendDayOfYear(3);
            break;
          case 'k':
            builder.appendOptional(DateTimeFormat.forPattern("' '").getParser());
            builder.appendHourOfDay(2);
            break;
          case 'l':
            builder.appendOptional(DateTimeFormat.forPattern("' '").getParser());
            builder.appendClockhourOfHalfday(2);
            break;
          case 'm':
            builder.appendMonthOfYear(2);
            break;
          case 'M':
            builder.appendMinuteOfHour(2);
            break;
          case 'n':
            break;
          case 'p':
            builder.appendHalfdayOfDayText();
            break;
          case 'r':
            builder.appendClockhourOfHalfday(2);
            builder.appendLiteral(':');
            builder.appendMinuteOfHour(2);
            builder.appendLiteral(':');
            builder.appendSecondOfMinute(2);
            builder.appendLiteral(' ');
            builder.appendHalfdayOfDayText();
            break;
          case 'R':
            builder.appendHourOfDay(2);
            builder.appendLiteral(':');
            builder.appendMinuteOfHour(2);
            break;
          case 'S':
            builder.appendSecondOfMinute(2);
            break;
          case 't':
            break;
          case 'T':
            builder.appendHourOfDay(2);
            builder.appendLiteral(':');
            builder.appendMinuteOfHour(2);
            builder.appendLiteral(':');
            builder.appendSecondOfMinute(2);
            break;
/*          case 'U':  //FIXME Joda does not support US week start (Sun), this will be wrong
            builder.appendWeekOfYear(2);
            break;
          case 'u':
            builder.appendDayOfWeek(1);
            break;*/
          case 'V':
            break; //accepted and ignored
/*          case 'w':  //FIXME Joda does not support US week start (Sun), this will be wrong
            builder.appendDayOfWeek(1);
            break;
          case 'W':
            builder.appendWeekOfYear(2);
            break;*/
          case 'x':
            builder.appendTwoDigitYear(2019);
            builder.appendLiteral('/');
            builder.appendMonthOfYear(2);
            builder.appendLiteral('/');
            builder.appendDayOfMonth(2);
            break;
/*          case 'X':  //Results differ between OSX and Linux
            builder.appendHourOfDay(2);
            builder.appendLiteral(':');
            builder.appendMinuteOfHour(2);
            builder.appendLiteral(':');
            builder.appendSecondOfMinute(2);
            break;*/
          case 'y': //POSIX 2004 & 2008 says 69-99 -> 1900s, 00-68 -> 2000s
            builder.appendTwoDigitYear(2019);
            break;
          case 'Y':
            builder.appendYear(4,4);
            break;
          case 'z':
            builder.appendTimeZoneOffset(null, "z", false, 2, 2);
            break;
          case 'Z':
            break;  //for output only, accepted and ignored for input
          default:  // No match, ignore
            builder.appendLiteral('\'');
            builder.appendLiteral(token);
            Log.warn(token + "is not acceptted as a parse token, treating as a literal");
        }
      } else {
        if (c == '\'') {
          String sub = token.substring(1);
          if (sub.length() > 0) {
            // Create copy of sub since otherwise the temporary quoted
            // string would still be referenced internally.
            builder.appendLiteral(new String(sub));
          }
        } else throw new IllegalArgumentException("Unexpected token encountered parsing format string:" + c);
      }
    }
  }

  /**
   * Parses an individual token.
   *
   * @param pattern  the pattern string
   * @param indexRef  a single element array, where the input is the start
   *  location and the output is the location after parsing the token
   * @return the parsed token
   */
  private static String parseToken(String pattern, int[] indexRef) {
    StringBuilder buf = new StringBuilder();

    int i = indexRef[0];
    int length = pattern.length();

    char c = pattern.charAt(i);
    if (c == '%' && i + 1 < length && pattern.charAt(i+1) != '%') {
      //Grab pattern tokens
      c = pattern.charAt(++i);
      //0 is ignored for input, and this ignores alternative religious eras
      if ((c == '0' || c == 'E') && i + 1 >= length) c = pattern.charAt(++i);
      buf.append('%');
      buf.append(c);
    } else { // Grab all else as text
      buf.append('\'');  // mark literals with ' in first place
      buf.append(c);
      for (i++; i < length;i++) {
        c = pattern.charAt(i);
        if (c == '%' ) { // consume literal % otherwise break
          if (i + 1 < length && pattern.charAt(i + 1) == '%') i++;
          else { i--; break; }
        }
        buf.append(c);
      }
    }

    indexRef[0] = i;
    return buf.toString();
  }

  private static long attemptTimeParse_2( ValueString str ) {
    final byte[] buf = str.getBuffer();
    int i=str.getOffset();
    final int end = i+str.length();
    while( i < end && buf[i] == ' ' ) i++;
    if   ( i < end && buf[i] == '"' ) i++;
    if( (end-i) < 8 ) return Long.MIN_VALUE;
    int yy=0, MM=0, dd=0;
    dd = digit(dd,buf[i++]);
    if( buf[i] != '-' ) dd = digit(dd,buf[i++]);
    if( dd < 1 || dd > 31 ) return Long.MIN_VALUE;
    if( buf[i++] != '-' ) return Long.MIN_VALUE;
    byte[]mm=null;
    OUTER: for( ; MM<MMS.length; MM++ ) {
      byte[][] mms = MMS[MM];
      INNER:
      for (byte[] mm1 : mms) {
        mm = mm1;
        if (mm == null) continue;
        if (i + mm.length >= end) continue INNER;
        for (int j = 0; j < mm.length; j++)
          if (mm[j] != Character.toLowerCase(buf[i + j]))
            continue INNER;
        if (buf[i + mm.length] == '-') break OUTER;
      }
    }
    if( MM == MMS.length ) return Long.MIN_VALUE; // No matching month
    i += mm.length;             // Skip month bytes
    MM++;                       // 1-based month
    if( buf[i++] != '-' ) return Long.MIN_VALUE;
    yy = digit(yy,buf[i++]);    // 2-digit year
    yy = digit(yy,buf[i++]);
    if( end-i>=2 && buf[i] != '"' ) {
      yy = digit(yy,buf[i++]);  // 4-digit year
      yy = digit(yy,buf[i++]);
    } else {
      yy += 2000;               // Y2K bug
    }
    if( i<end && buf[i] == '"' ) i++;
    if( i<end ) return Long.MIN_VALUE;
    return encodeTimePat(new DateTime(yy,MM,dd,0,0,0, getTimezone()).getMillis(),2);
  }
}
