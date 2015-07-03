package water;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.util.ArrayUtils;
import water.util.IcedBitSet;

import java.util.Random;

public class IcedBitSetTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  static void check(IcedBitSet bs, int bitoff, Integer[] idx) {
    String correct = "{";
    if (bitoff > 0) {
      correct += "..." + bitoff + " 0-bits... ";
    }
    for (int i=bitoff; i<bs.size(); ++i) {
      if (ArrayUtils.find(idx, i) != -1) {
        Assert.assertTrue(bs.contains(i));
        correct +="1";
      }
      else {
        Assert.assertTrue(!bs.contains(i));
        correct +="0";
      }
      if ((i-bitoff+1)%8 == 0 && i != bs.size()-1) correct += " ";
    }
    correct += "}";
    String s = bs.toString();
//    System.out.println(s);
//    System.out.println(correct);
    Assert.assertTrue(s.equals(correct));
  }

  static void fill(IcedBitSet bs, Integer[] idx) {
    Random rng = new Random();
    for (int i = 0; i < idx.length; ++i) {
      idx[i] = rng.nextInt(bs.size());
      bs.set(idx[i]);
    }
  }

  @Test public void fill8() {
    int len = 8;
    IcedBitSet bs = new IcedBitSet(len);
    Integer[] idx = new Integer[(int) Math.floor(len / 2)];
    fill(bs, idx);
    Assert.assertEquals(bs.size(), 32);
    check(bs, 0, idx);
  }

  @Test public void fill17() {
    int len = 17;
    IcedBitSet bs = new IcedBitSet(len);
    Integer[] idx = new Integer[(int) Math.floor(len / 2)];
    fill(bs, idx);
    Assert.assertEquals(bs.size(), 32);
    check(bs, 0, idx);
  }

  @Test public void fill16() {
    int len = 16;
    IcedBitSet bs = new IcedBitSet(len);
    Integer[] idx = new Integer[(int) Math.floor(len / 2)];
    fill(bs, idx);
    Assert.assertEquals(bs.size(), 32);
    check(bs, 0, idx);
  }

  @Test public void fill32() {
    int len = 32;
    IcedBitSet bs = new IcedBitSet(len);
    Integer[] idx = new Integer[(int) Math.floor(len / 2)];
    fill(bs, idx);
    Assert.assertEquals(bs.size(), len);
    check(bs, 0, idx);
  }

  @Test public void fill33() {
    int len = 33;
    IcedBitSet bs = new IcedBitSet(len);
    Integer[] idx = new Integer[(int) Math.floor(len / 2)];
    fill(bs, idx);
    Assert.assertEquals(bs.size(), Math.max(32,len));
    check(bs, 0, idx);
  }

  @Test public void fillHalf() {
    int len = 10 + (int) (10000 * new Random().nextDouble());
    IcedBitSet bs = new IcedBitSet(len);
    Integer[] idx = new Integer[(int) Math.floor(len / 2)];
    fill(bs, idx);
    Assert.assertEquals(bs.size(), Math.max(32,len));
    check(bs, 0, idx);
  }

  @Test (expected = IndexOutOfBoundsException.class) public void outOfBounds() {
    int len = 32 + (int) (10000 * new Random().nextDouble());
    IcedBitSet bs = new IcedBitSet(len);
    bs.set(len);
  }

  @Test public void fillSparse() {
    int len = 10 + (int) (10000 * new Random().nextDouble());
    IcedBitSet bs = new IcedBitSet(len);
    Integer[] idx = new Integer[(int) Math.floor(len / 200)];
    fill(bs, idx);
    Assert.assertEquals(bs.size(), Math.max(32,len));
    check(bs, 0, idx);
  }

  @Test public void clear() {
    int len = 10 + (int) (10000 * new Random().nextDouble());
    IcedBitSet bs = new IcedBitSet(len);
    Integer[] idx = new Integer[(int) Math.floor(len / 200)];
    fill(bs, idx);
    Assert.assertEquals(bs.size(), Math.max(32,len));
    check(bs, 0, idx);

    for (int i = 0; i < idx.length; ++i) {
      bs.clear(idx[i]);
    }
    check(bs, 0, new Integer[]{});
  }

  @Test public void bitOff() {
    int len = 113 + (int) (10000 * new Random().nextDouble());
    int bitoff = (int) (100 * new Random().nextDouble());
    IcedBitSet bs = new IcedBitSet(len,bitoff);
    Integer[] idx = new Integer[(int) Math.floor(len / 4)];
    Random rng = new Random();
    for (int i = 0; i < idx.length; ) {
      int val = rng.nextInt(len);
      if (val > bitoff) {
        idx[i] = val;
        bs.set(idx[i]);
        ++i;
      }
    }
    Assert.assertEquals(bs.size(), len);
    check(bs, bitoff, idx);

    for (int i = 0; i < idx.length; ++i) {
      bs.clear(idx[i]);
    }
    check(bs, bitoff, new Integer[]{});
  }
}
