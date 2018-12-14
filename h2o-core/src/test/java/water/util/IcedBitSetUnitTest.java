package water.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;

public class IcedBitSetUnitTest {

  @Test (expected = AssertionError.class) 
  public void outOfBounds() {
    int len = 32 + (int) (10000 * new Random().nextDouble());
    IcedBitSet bs = new IcedBitSet(len);
    bs.set(len);
  }

  @Test
  public void byteTo8digitBinaryString() {
    Assert.assertEquals("00000001", IcedBitSet.byteTo8digitBinaryString((byte) 0x01));
    Assert.assertEquals("01000001", IcedBitSet.byteTo8digitBinaryString((byte) 0x41));
    Assert.assertEquals("11111110", IcedBitSet.byteTo8digitBinaryString((byte) -2));
  }

}
