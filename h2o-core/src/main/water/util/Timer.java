package water.util;

import java.text.SimpleDateFormat;
import java.util.Date;
/**
 * Simple Timer class.
 **/
public class Timer {

  /** SimpleDataFormat is not thread safe. To avoid constructing them repeatedly we store them into thread
   * local variables. */
  private static final ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
    @Override protected SimpleDateFormat initialValue() {
      SimpleDateFormat format = new SimpleDateFormat("dd-MMM hh:mm:ss.SSS");
      return format;
    }
  };
  private static final ThreadLocal<SimpleDateFormat> shortFormat = new ThreadLocal<SimpleDateFormat>() {
    @Override protected SimpleDateFormat initialValue() {
      SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss.SSS");
      return format;
    }
  };

  public final long _start = System.currentTimeMillis();
  public final long _nanos = System.nanoTime();

  /**Return the difference between when the timer was created and the current time. */
  public long time() { return System.currentTimeMillis() - _start; }
  public long nanos(){ return System.nanoTime() - _nanos; }

  /**Return the difference between when the timer was created and the current time as a
   * string along with the time of creation in date format. */
  public String toString() {
    final long now = System.currentTimeMillis();
    return PrettyPrint.msecs(now - _start, false) + " (Wall: " + dateFormat.get().format(new Date(now)) + ") ";
  }

  /** return the start time of this timer.**/
  public String startAsString() { return dateFormat.get().format(new Date(_start)); }
  /** return the start time of this timer.**/
  public String startAsShortString() { return shortFormat.get().format(new Date(_start)); }

}
