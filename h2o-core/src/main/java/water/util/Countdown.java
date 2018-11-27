package water.util;

import java.io.Serializable;

/**
 * Simple countdown to encapsulate timeouts and durations.
 * time_limit_millis <= 0 is interpreted as infinite countdown (no timeout)
 */
public class Countdown implements Serializable {

  private long _time_limit_millis;
  private long _start_time;


  public Countdown(long time_limit_millis) {
    _time_limit_millis = time_limit_millis;
  }

  public Countdown(long _time_limit_millis, boolean start) {
    this(_time_limit_millis);
    if (start) start();
  }

  public void start() {
    if (started()) throw new IllegalStateException("Countdown already started");
    _start_time = now();
  }

  public boolean started() {
    return _start_time > 0;
  }

  public long stop() {
    long duration = elapsedTime();
    reset();
    return duration;
  }

  public long elapsedTime() {
    if (!started()) throw new IllegalStateException("Countdown was not started");
    return now() - _start_time;
  }

  public long remainingTime() {
    if (!started()) throw new IllegalStateException("Countdown was not started");
    return _time_limit_millis > 0
        ? Math.max(0, _start_time + _time_limit_millis - now())
        : Long.MAX_VALUE;
  }

  public void reset() {
    _start_time = 0;
  }

  public boolean timedOut() {
    return started() && _time_limit_millis > 0 && now() - _start_time > _time_limit_millis;
  }

  private long now() { return System.currentTimeMillis(); }
}
