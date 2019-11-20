package water.init;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import water.AutoBuffer;
import water.H2O;
import water.TestBase;
import water.util.ArrayUtils;
import water.util.MathUtils;
import water.webserver.iface.H2OHttpConfig;
import water.webserver.iface.LoginType;

import static water.TestUtil.ar;
import static water.TestUtil.ari;

/**
 * Test to verify correctness of network algebra.
 */
public class NetworkInitTest extends TestBase {

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

  @Test
  public void testWebServerConfig() {
    H2O.OptArgs args = new H2O.OptArgs();
    args.jks = "/path/to/file.jks";
    args.jks_pass = "h2opass";
    args.jks_alias = "h2oalias";
    args.spnego_properties = "h2oprops";
    args.form_auth = true;
    args.session_timeout = 100;
    args.user_name = "testuser";
    args.context_path = "testcontext";

    H2OHttpConfig cfg = NetworkInit.webServerConfig(args);

    H2OHttpConfig expected = new H2OHttpConfig();
    expected.jks = "/path/to/file.jks";
    expected.jks_pass = "h2opass";
    expected.jks_alias = "h2oalias";
    expected.spnego_properties = "h2oprops";
    expected.form_auth = true;
    expected.session_timeout = 100;
    expected.user_name = "testuser";
    expected.context_path = "testcontext";
    expected.loginType = LoginType.NONE;
    
    Assert.assertEquals(expected, cfg);
  }


}
