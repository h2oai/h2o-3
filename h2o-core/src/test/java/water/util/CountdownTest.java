package water.util;

import org.junit.Assert;
import org.junit.Test;
import water.TestBase;

import static org.junit.Assert.*;

public class CountdownTest extends TestBase {
  
  private void short_sleep() {
    try {
      Thread.sleep(100);
    } catch (InterruptedException ignored) {}
  }
  
  @Test
  public void testStateAfterStart() {
    Countdown c = new Countdown(1000);
    assertFalse(c.running());
    assertFalse(c.ended());
    c.start();
    assertTrue(c.running());
    assertFalse(c.ended());
    assertFalse(c.timedOut());
  }

  @Test
  public void testCannotBeStartedTwice() {
    Countdown c = new Countdown(1000);
    c.start();
    try {
      c.start();
      fail("Starting started countdown should have thrown IllegalStateException");
    } catch (IllegalStateException e) {
      Assert.assertTrue(e.getMessage().contains("already running"));
    }
  }
  
  @Test
  public void testCanRestartAfterReset() {
    Countdown c = new Countdown(1000);
    c.start();
    c.reset();
    assertFalse(c.running());
    c.start();
    assertTrue(c.running());
  }
  
  @Test
  public void testStateAfterStop() {
    Countdown c = new Countdown(1000);
    c.start();
    short_sleep();
    assertFalse(c.ended());
    long duration = c.stop();
    assertTrue(c.ended());
    assertFalse(c.running());
    assertFalse(c.timedOut());
    assertTrue(duration > 0);
  }


  @Test
  public void testElapsedTime() {
    Countdown c = new Countdown(1000);
    assertEquals(0, c.elapsedTime());
    c.start();
    short_sleep(); //100 millis
    assertTrue("Elapsed time should have been close to 100 millis, got instead: "+c.elapsedTime(), Math.abs(c.elapsedTime() - 100) < 10);
    c.stop();
    assertTrue("Elapsed time should have been close to 100 millis, got instead: "+c.elapsedTime(), Math.abs(c.elapsedTime() - 100) < 10);
    assertEquals(c.elapsedTime(), c.duration());
    c.reset();
    assertEquals(0, c.elapsedTime());
  }

  @Test
  public void testRemainingTime() {
    Countdown c = new Countdown(1000);
    assertEquals(1000, c.remainingTime());
    c.start();
    short_sleep(); //100 millis
    assertTrue("Remaining time should have been close to 900 millis, got instead: "+c.remainingTime(), Math.abs(1000 - c.remainingTime() - 100) < 10);
    c.stop();
    assertEquals(0, c.remainingTime());
    c.reset();
    assertEquals(1000, c.remainingTime());
  }

  @Test
  public void testDuration() {
    Countdown c = new Countdown(1000);
    try {
      c.duration();
      fail("should have thrown IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Countdown was never started"));
    }
    c.start();
    try {
      c.duration();
      fail("should have thrown IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Countdown was never started or stopped"));
    }
    short_sleep(); //100 millis
    c.stop();
    assertTrue("Duration should have been close to 100 millis, got instead: "+c.duration(), Math.abs(c.duration() - 100) < 10);
    c.reset();
    try {
      c.duration();
      fail("should have thrown IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Countdown was never started"));
    }
  }
  
}
