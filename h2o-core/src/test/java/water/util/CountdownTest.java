package water.util;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class CountdownTest {
  
  private void short_sleep() {
    try {
      Thread.sleep(3);
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
  
}
