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
    c.start();
    assertTrue(c.started());
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
      Assert.assertTrue(e.getMessage().contains("already started"));
    }
  }
  
  @Test
  public void testCanRestartAfterReset() {
    Countdown c = new Countdown(1000);
    c.start();
    c.reset();
    assertFalse(c.started());
    c.start();
    assertTrue(c.started());
  }
  
  @Test
  public void testStateAfterStop() {
    Countdown c = new Countdown(1000);
    c.start();
    short_sleep();
    long duration = c.stop();
    assertFalse(c.started());
    assertFalse(c.timedOut());
    assertTrue(duration > 0);
  }
  
}
