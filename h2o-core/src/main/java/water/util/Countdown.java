package water.util;

import water.Iced;

import java.util.Date;

/**
 * Simple countdown to encapsulate timeouts and durations.
 * time_limit_millis <= 0 is interpreted as infinite countdown (no timeout).
 * All durations in this class are milliseconds:
 * <li>
 *   <ul>{@link #duration()}</ul>
 *   <ul>{@link #elapsedTime()}</ul>
 *   <ul>{@link #remainingTime()}</ul>
 * </li>
 */
public class Countdown extends Iced<Countdown> {

  private long _time_limit_millis;
  private long _start_time;
  private long _stop_time;

  public static Countdown fromSeconds(double seconds) {
    return new Countdown(seconds <= 0 ? 0 : (long)Math.ceil(seconds * 1000));
  }

  public Countdown(long time_limit_millis) {
    _time_limit_millis = time_limit_millis;
  }

  public Countdown(long _time_limit_millis, boolean start) {
    this(_time_limit_millis);
    if (start) start();
  }

  public Date start_time() {
    return new Date(_start_time);
  }

  public Date stop_time() {
    return new Date(_stop_time);
  }

  public long duration() {
    if (!ended()) throw new IllegalStateException("Countdown was never started or stopped.");
    return elapsedTime();
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
    if (running() && !ended())
      _stop_time = now();
    return elapsedTime();
  }

  public long elapsedTime() {
    if (running()) return now() - _start_time;
    if (ended()) return _stop_time - _start_time;
    return 0;
  }

  public long remainingTime() {
    if (running()) return _time_limit_millis > 0 ? Math.max(0, _start_time + _time_limit_millis - now()) : Long.MAX_VALUE;
    if (ended()) return 0;
    return _time_limit_millis > 0 ? _time_limit_millis : Long.MAX_VALUE;
  }

  public void reset() {
    _start_time = 0;
    _stop_time = 0;
  }

  public boolean timedOut() {
    return _start_time > 0 && _time_limit_millis > 0 && elapsedTime() > _time_limit_millis;
  }

  private long now() { return System.currentTimeMillis(); }

}
