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

  @Test
  public void HEXDEV_725_snippet1() {
      // near line 80846 - snippet 1
    final byte[] GRPSPLIT0 = new byte[]{-2, -1, -1, -1, -1, -65, -1, -1, -1, -3, -3, -1, -1, -1, -1, -1, -1, -1, -1, -1, -5, -9, -1, -1, -1, -33, -1, -1, -1, -1, 63, -1, -1, -2, -1, -2, 127, -2, -1, -1, -1, -1, -2, -9, -1, -1, -1, -1, -1, -9, -1, -1, 94, -5, -17, 15};
    final IcedBitSet bitSet = new IcedBitSet(GRPSPLIT0.length * 8);
    bitSet.fill(GRPSPLIT0, 0, GRPSPLIT0.length * 8, 4);

    final String code = bitSet.toStrArray();
    Assert.assertEquals("Error in generated code",
        "{-2, -1, -1, -1, -1, -65, -1, -1, -1, -3, -3, -1, -1, -1, -1, -1, -1, -1, -1, -1, -5, -9, -1, -1, -1, -33, -1, -1, -1, -1, 63, -1, -1, -2, -1, -2, 127, -2, -1, -1, -1, -1, -2, -9, -1, -1, -1, -1, -1, -9, -1, -1, 94, -5, -17, 15}",
        code);

    final String comment = bitSet.toString();
    Assert.assertEquals("Error in generated comment",
         "{...4 0-bits... 11111110 11111111 11111111 11111111 11111111 10111111 11111111 11111111 11111111 11111101 11111101 11111111 11111111 11111111 11111111 11111111 11111111 11111111 11111111 11111111 11111011 11110111 11111111 11111111 11111111 11011111 11111111 11111111 11111111 11111111 00111111 11111111 11111111 11111110 11111111 11111110 01111111 11111110 11111111 11111111 11111111 11111111 11111110 11110111 11111111 11111111 11111111 11111111 11111111 11110111 11111111 11111111 01011110 11111011 11101111 00001111 }",
        comment);
  }

  @Test
  public void HEXDEV_725_snippet2() {
    // near line 12608 - snippet 2
    final byte[] GRPSPLIT7 = new byte[]{-1, -2, -11, -1, -1, -1, -29, -5, 87, 126};
    final IcedBitSet bitSet = new IcedBitSet(GRPSPLIT7.length * 8);
    bitSet.fill(GRPSPLIT7, 0, GRPSPLIT7.length * 8, 17);

    final String code = bitSet.toStrArray();
    Assert.assertEquals("Error in generated code",
        "{-1, -2, -11, -1, -1, -1, -29, -5, 87, 126}",
        code);

    final String comment = bitSet.toString();
    Assert.assertEquals("Error in generated comment",
        "{...17 0-bits... 11111111 11111110 11110101 11111111 11111111 11111111 11100011 11111011 01010111 01111110 }",
        comment);
  }

  @Test
  public void HEXDEV_725_snippet3() {
    // near line 12608 - snippet 2
    final byte[] GRPSPLIT2 = new byte[]{119, -5, -1, -1, -1, -3, -1, 127, -21, 78, 111, 29};
    final IcedBitSet bitSet = new IcedBitSet(GRPSPLIT2.length * 8);
    bitSet.fill(GRPSPLIT2, 0, GRPSPLIT2.length * 8, 1);

    final String code = bitSet.toStrArray();
    Assert.assertEquals("Error in generated code",
        "{119, -5, -1, -1, -1, -3, -1, 127, -21, 78, 111, 29}",
        code);

    final String comment = bitSet.toString();
    Assert.assertEquals("Error in generated comment",
        "{...1 0-bits... 01110111 11111011 11111111 11111111 11111111 11111101 11111111 01111111 11101011 01001110 01101111 00011101 }",
        comment);
  }
}
