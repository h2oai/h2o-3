package water.util.fp;

/**
 * A reader that returns time. By default it returns current time,
 * but it allows to get abstracted from real time while running tests.
 * Created by vpatryshev on 4/7/17.
 */
public abstract class TimeReader implements Reader<Long> {
  
  public static TimeReader SYSTEM = new TimeReader() {
    public Long read() { return System.currentTimeMillis(); }
  };
    
}
