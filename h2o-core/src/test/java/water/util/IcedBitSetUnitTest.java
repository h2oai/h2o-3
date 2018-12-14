package water.util;

import org.junit.Assert;
import org.junit.Test;

public class IcedBitSetUnitTest {

  @Test
  public void byteTo8digitBinaryString() {
    Assert.assertEquals("00000001", IcedBitSet.byteTo8digitBinaryString((byte) 0x01));
    Assert.assertEquals("01000001", IcedBitSet.byteTo8digitBinaryString((byte) 0x41));
    Assert.assertEquals("11111110", IcedBitSet.byteTo8digitBinaryString((byte) -2));
  }

}
