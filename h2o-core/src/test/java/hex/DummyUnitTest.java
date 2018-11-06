package hex;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class DummyUnitTest {

  @Test
  public void empty() {
    System.out.println("AAAAAA");
  }

  @Test
  public void passing() {
    Assert.assertTrue(true);
  }

  @Ignore
  @Test
  public void failing() {
    Assert.assertTrue(false);
  }

  @Ignore
  @Test
  public void crashing() {
    throw new RuntimeException("crash");
  }
}
