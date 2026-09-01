package water;

import org.junit.Assert;
import org.junit.Test;

public class IcedWrapperTest {

  @Test
  public void testBooleanArrayGet() {
    boolean[] values = new boolean[]{true, false, true};
    IcedWrapper wrapper = new IcedWrapper(values);
    Assert.assertArrayEquals(values, (boolean[]) wrapper.get());
  }

  @Test
  public void testBooleanArrayWriteUnwrappedJSON() {
    IcedWrapper wrapper = new IcedWrapper(new boolean[]{true, false});
    String json = new String(wrapper.writeUnwrappedJSON(new AutoBuffer()).buf());
    Assert.assertEquals("[true,false]", json);
  }
}
