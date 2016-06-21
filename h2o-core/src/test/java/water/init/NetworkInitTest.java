package water.init;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import water.AutoBuffer;
import water.util.ArrayUtils;
import water.util.MathUtils;

import static water.TestUtil.ar;
import static water.TestUtil.ari;

/**
 * Test to verify correctness of network algebra.
 */
public class NetworkInitTest {
  @Test
  public void testIPV4CidrBlocks() {
    NetworkInit.CIDRBlock c1 = NetworkInit.CIDRBlock.parse("128.0.0.1/32");
    Assert.assertEquals(32, c1.bits);
    Assert.assertArrayEquals(ari(128, 0, 0, 1), c1.ip);
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(ari(128, 0, 0, 1))));
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(ari(128, 0, 0, 2))));

    c1 = NetworkInit.CIDRBlock.parse("128.0.0.1/0");
    Assert.assertEquals(0, c1.bits);
    Assert.assertArrayEquals(ari(128, 0, 0, 1), c1.ip);
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(ari(128, 0, 0, 1))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(ari(128, 0, 0, 2))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(ari(255, 255, 255, 255))));

    c1 = NetworkInit.CIDRBlock.parse("10.10.1.32/27");
    Assert.assertEquals(27, c1.bits);
    Assert.assertArrayEquals(ari(10, 10, 1, 32), c1.ip);
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(ari(10, 10, 1, 44))));
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(ari(10, 10, 1, 90))));

    c1 = NetworkInit.CIDRBlock.parse("128.0.0.1/42");
    Assert.assertNull(c1);

    c1 = NetworkInit.CIDRBlock.parse("128.1/21");
    Assert.assertNull(c1);

    c1 = NetworkInit.CIDRBlock.parse("1.1.257.1/21");
    Assert.assertNull(c1);
  }

  @Test
  public void testIPV6CidrBlocks() {
    NetworkInit.CIDRBlock c1 = NetworkInit.CIDRBlock.parse("0:0:0:0:0:0:0:1/128");
    Assert.assertEquals(128, c1.bits);
    Assert.assertArrayEquals(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 1)), c1.ip);
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 1)))));
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 2)))));

    c1 = NetworkInit.CIDRBlock.parse("0:0:0:0:0:0:0:1/0");
    Assert.assertEquals(0, c1.bits);
    Assert.assertArrayEquals(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 1)), c1.ip);
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 1)))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 2)))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff)))));

    c1 = NetworkInit.CIDRBlock.parse("2001:db8:1234:0:0:0:0:0/48");
    Assert.assertEquals(48, c1.bits);
    Assert.assertArrayEquals(toOctects(ari(0x2001, 0xdb8, 0x1234, 0, 0, 0, 0, 0)), c1.ip);
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 1)))));
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0x2001, 0xdb8, 0x1233, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff)))));
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0x2001, 0xdb8, 0x1235, 0, 0, 0, 0, 0)))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0x2001, 0xdb8, 0x1234, 0, 0, 0, 0, 0))))); // First address in the block
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0x2001, 0xdb8, 0x1234, 0x0001, 0x9988, 0x7766, 0xdead, 0xbabe)))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0x2001, 0xdb8, 0x1234, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff))))); // The last address in the block

    c1 = NetworkInit.CIDRBlock.parse("0:0:0:0:0:0:0:1/129");
    Assert.assertNull(c1);

    c1 = NetworkInit.CIDRBlock.parse("::1/128");
    Assert.assertNull(c1);

    c1 = NetworkInit.CIDRBlock.parse("1.1.257.1/42");
    Assert.assertNull(c1);
  }

  // Test for H2OKey
  @Test public void testIPv6AddressEncoding() {
    byte[] address = toByte(toOctects(ari(0x2001, 0xdb8, 0x1234, 0x0001, 0x9988, 0x7766, 0xdead, 0xbabe)));
    long high = ArrayUtils.encodeAsLong(address, 8, 8);
    long low = ArrayUtils.encodeAsLong(address, 0, 8);
    AutoBuffer ab = new AutoBuffer();
    byte[] returnedAddress = ab.put8(low).put8(high).flipForReading().getA1(16);
    Assert.assertArrayEquals(address, returnedAddress);
  }

  // Test for H2OKey
  @Test public void testIPv4AddressEncoding() {
    byte[] address = toByte(ari(10, 10, 1, 44));
    int ipv4 = (int) ArrayUtils.encodeAsLong(address);
    AutoBuffer ab = new AutoBuffer();
    byte[] returnedAddress = ab.put4(ipv4).flipForReading().getA1(4);
    Assert.assertArrayEquals(address, returnedAddress);
  }

  @Test public void testUnsignedOps() {
    Assert.assertEquals(-1, MathUtils.compareUnsigned(0x00L, 0xFFL));
    Assert.assertEquals(-1, MathUtils.compareUnsigned(0xFFFFFFFFFFFFFFFEL, 0xFFFFFFFFFFFFFFFFL));
    Assert.assertEquals(-1, MathUtils.compareUnsigned(0xFFFFFFFFFFFFFFFFL & ~0x80FFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL));
    Assert.assertEquals( 0, MathUtils.compareUnsigned(0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL));
    Assert.assertEquals(-1, MathUtils.compareUnsigned(0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFEL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL));
    Assert.assertEquals(-1, MathUtils.compareUnsigned(0x00L, 0xFFFFFFFFFFFFFFFFL, 0x01L, 0xFFFFFFFFFFFFFFFFL));
    Assert.assertEquals(-1, MathUtils.compareUnsigned(0x00L, 0x00L, 0x00L, 0x01L));
  }

  static byte[] toByte(int[] ary) {
    return ArrayUtils.toByteArray(ary);
  }

  static int[] toOctects(int[] doubleOctets) {
    int[] r = new int[doubleOctets.length*2];
    for (int i = 0; i < doubleOctets.length; i++) {
      r[2*i + 0] = (doubleOctets[i] >> 8) & 0xff;
      r[2*i + 1] = doubleOctets[i] & 0xff;
    }
    return r;
  }
}
