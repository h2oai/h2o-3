package water.util;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Simple Timer class.
 **/
public class Timer {

  private static final DateTimeFormatter longFormat = DateTimeFormat.forPattern("dd-MMM HH:mm:ss.SSS");
  private static final DateTimeFormatter shortFormat= DateTimeFormat.forPattern(       "HH:mm:ss.SSS");
  private static final DateTimeFormatter logFormat  = DateTimeFormat.forPattern( "MM-dd HH:mm:ss.SSS");

  final long _start = System.currentTimeMillis();
  final long _nanos = System.nanoTime();

  /**Return the difference between when the timer was created and the current time. */
  public long time() { return System.currentTimeMillis() - _start; }
  public long nanos(){ return System.nanoTime() - _nanos; }

  /** Return the difference between when the timer was created and the current
   *  time as a string along with the time of creation in date format. */
  @Override public String toString() {
    final long now = System.currentTimeMillis();
    return PrettyPrint.msecs(now - _start, false) + " (Wall: " + longFormat.print(now) + ") ";
  }

  /** return the start time of this timer.**/
  String startAsString() { return longFormat.print(_start); }
  /** return the start time of this timer.**/
  String startAsShortString() { return shortFormat.print(_start); }

  /**
   * Used by Logging (Log.java) for creating a timestamp in front of each output line.
   */
  static String nowAsLogString() { return logFormat.print(DateTime.now()); }
}
