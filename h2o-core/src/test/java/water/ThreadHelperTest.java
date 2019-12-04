package water;

import org.junit.Test;

import static org.junit.Assert.*;

public class ThreadHelperTest {

  @Test
  public void initCommonThreadProperties() {
    Thread t_regular = new Thread();
    ThreadHelper.initCommonThreadProperties(new H2O.OptArgs(), t_regular);
    assertFalse(t_regular.isDaemon());

    Thread t_embedded = new Thread();
    H2O.OptArgs embeddedArgs = new H2O.OptArgs();
    embeddedArgs.embedded = true;
    ThreadHelper.initCommonThreadProperties(embeddedArgs, t_embedded);
    assertTrue(t_embedded.isDaemon());
  }

}
