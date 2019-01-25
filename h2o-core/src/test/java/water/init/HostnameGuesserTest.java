package water.init;

import org.junit.Assert;
import org.junit.Test;

import static water.TestUtil.ari;
import static water.init.HostnameGuesser.CIDRBlock;
import static water.init.NetworkInitTest.toByte;
import static water.init.NetworkInitTest.toOctects;

public class HostnameGuesserTest {

  @Test
  public void testIPV4CidrBlocks() {
    CIDRBlock c1 = CIDRBlock.parse("128.0.0.1/32");
    Assert.assertEquals(32, c1.bits);
    Assert.assertArrayEquals(ari(128, 0, 0, 1), c1.ip);
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(ari(128, 0, 0, 1))));
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(ari(128, 0, 0, 2))));

    c1 = CIDRBlock.parse("128.0.0.1/0");
    Assert.assertEquals(0, c1.bits);
    Assert.assertArrayEquals(ari(128, 0, 0, 1), c1.ip);
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(ari(128, 0, 0, 1))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(ari(128, 0, 0, 2))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(ari(255, 255, 255, 255))));

    c1 = CIDRBlock.parse("10.10.1.32/27");
    Assert.assertEquals(27, c1.bits);
    Assert.assertArrayEquals(ari(10, 10, 1, 32), c1.ip);
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(ari(10, 10, 1, 44))));
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(ari(10, 10, 1, 90))));

    c1 = CIDRBlock.parse("128.0.0.1/42");
    Assert.assertNull(c1);

    c1 = CIDRBlock.parse("128.1/21");
    Assert.assertNull(c1);

    c1 = CIDRBlock.parse("1.1.257.1/21");
    Assert.assertNull(c1);
  }

  @Test
  public void testIPV6CidrBlocks() {
    CIDRBlock c1 = CIDRBlock.parse("0:0:0:0:0:0:0:1/128");
    Assert.assertEquals(128, c1.bits);
    Assert.assertArrayEquals(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 1)), c1.ip);
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 1)))));
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 2)))));

    c1 = CIDRBlock.parse("0:0:0:0:0:0:0:1/0");
    Assert.assertEquals(0, c1.bits);
    Assert.assertArrayEquals(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 1)), c1.ip);
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 1)))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 2)))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff)))));

    c1 = CIDRBlock.parse("2001:db8:1234:0:0:0:0:0/48");
    Assert.assertEquals(48, c1.bits);
    Assert.assertArrayEquals(toOctects(ari(0x2001, 0xdb8, 0x1234, 0, 0, 0, 0, 0)), c1.ip);
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0, 0, 0, 0, 0, 0, 0, 1)))));
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0x2001, 0xdb8, 0x1233, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff)))));
    Assert.assertFalse(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0x2001, 0xdb8, 0x1235, 0, 0, 0, 0, 0)))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0x2001, 0xdb8, 0x1234, 0, 0, 0, 0, 0))))); // First address in the block
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0x2001, 0xdb8, 0x1234, 0x0001, 0x9988, 0x7766, 0xdead, 0xbabe)))));
    Assert.assertTrue(c1.isInetAddressOnNetwork(toByte(toOctects(ari(0x2001, 0xdb8, 0x1234, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff))))); // The last address in the block

    c1 = CIDRBlock.parse("0:0:0:0:0:0:0:1/129");
    Assert.assertNull(c1);

    c1 = CIDRBlock.parse("::1/128");
    Assert.assertNull(c1);

    c1 = CIDRBlock.parse("1.1.257.1/42");
    Assert.assertNull(c1);
  }

}