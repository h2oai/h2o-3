package water.util;

import water.Iced;

/**
 * Simple countdown to encapsulate timeouts and durations.
 * time_limit_millis <= 0 is interpreted as infinite countdown (no timeout)
 */
public class Countdown extends Iced<Countdown> {

  private long _time_limit_millis;
  private long _start_time;
  private long _stop_time;
  
  public static Countdown fromSeconds(double seconds) {
    return new Countdown(seconds <= 0 ? 0 : Math.round(seconds * 1000) + 1);
  }

  public Countdown(long time_limit_millis) {
    _time_limit_millis = time_limit_millis;
  }

  public Countdown(long _time_limit_millis, boolean start) {
    this(_time_limit_millis);
    if (start) start();
  }
  
  public long start_time() {
    return _start_time;
  }
  
  public long stop_time() {
    return _stop_time;
  }
  
  public long duration() {
    try {
      return elapsedTime();
    } catch (IllegalStateException e) {
      return 0;
    }
  }

  public void start() {
    if (running()) throw new IllegalStateException("Countdown is already running.");
    reset();
    _start_time = now();
  }

  public boolean running() {
    return _start_time > 0 && _stop_time == 0;
  }
  
  public boolean ended() {
    return _start_time > 0 && _stop_time > 0;
  }

  public long stop() {
    _stop_time = now();
    return elapsedTime();
  }
  
  public long elapsedTime() {
    if (running()) {
      return now() - _start_time;
    } else if (ended()) {
      return _stop_time - _start_time;
    } else {
      throw new IllegalStateException("Countdown was never started.");
    }
  }

  public long remainingTime() {
    if (!running()) throw new IllegalStateException("Countdown is not running.");
    return _time_limit_millis > 0
        ? Math.max(0, _start_time + _time_limit_millis - now())
        : Long.MAX_VALUE;
  }

  public void reset() {
    _start_time = 0;
    _stop_time = 0;
  }

  public boolean timedOut() {
    return running() && _time_limit_millis > 0 && elapsedTime() > _time_limit_millis;
  }

  private long now() { return System.currentTimeMillis(); }
  
}
