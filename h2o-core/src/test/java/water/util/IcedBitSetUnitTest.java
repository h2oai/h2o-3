package water.util;

import org.junit.Assert;
import org.junit.Test;
import water.TestBase;

import java.util.Random;

public class IcedBitSetUnitTest extends TestBase {

  @Test (expected = AssertionError.class)
  public void outOfBounds() {
    int len = 32 + (int) (10000 * new Random().nextDouble());
    IcedBitSet bs = new IcedBitSet(len);
    bs.set(len);
  }

  @Test
  public void byteTo8digitBinaryString() {
    Assert.assertEquals("10000000", IcedBitSet.byteToBinaryString((byte) 0x01, 8));
    Assert.assertEquals("10000010", IcedBitSet.byteToBinaryString((byte) 0x41, 10));
    Assert.assertEquals("01111111", IcedBitSet.byteToBinaryString((byte) -2, 22));
    Assert.assertEquals("011", IcedBitSet.byteToBinaryString((byte) -2, 3));
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
         "{...4 0-bits... 01111111 11111111 11111111 11111111 11111111 11111101 11111111 11111111 11111111 10111111 10111111 11111111 11111111 11111111 11111111 11111111 11111111 11111111 11111111 11111111 11011111 11101111 11111111 11111111 11111111 11111011 11111111 11111111 11111111 11111111 11111100 11111111 11111111 01111111 11111111 01111111 11111110 01111111 11111111 11111111 11111111 11111111 01111111 11101111 11111111 11111111 11111111 11111111 11111111 11101111 11111111 11111111 01111010 11011111 11110111 11110000}",
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
        "{...17 0-bits... 11111111 01111111 10101111 11111111 11111111 11111111 11000111 11011111 11101010 01111110}",
        comment);
  }

  @Test
  public void HEXDEV_725_snippet3() {
    // near line 82609 - snippet 3
    final byte[] GRPSPLIT2 = new byte[]{119, -5, -1, -1, -1, -3, -1, 127, -21, 78, 111, 29};
    final IcedBitSet bitSet = new IcedBitSet(GRPSPLIT2.length * 8);
    bitSet.fill(GRPSPLIT2, 0, GRPSPLIT2.length * 8, 1);

    final String code = bitSet.toStrArray();
    Assert.assertEquals("Error in generated code",
        "{119, -5, -1, -1, -1, -3, -1, 127, -21, 78, 111, 29}",
        code);

    final String comment = bitSet.toString();
    Assert.assertEquals("Error in generated comment",
        "{...1 0-bits... 11101110 11011111 11111111 11111111 11111111 10111111 11111111 11111110 11010111 01110010 11110110 10111000}",
        comment);
  }
}
