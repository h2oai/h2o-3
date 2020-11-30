package water.init;

import org.junit.Assert;
import org.junit.Test;

import water.AutoBuffer;
import water.H2O;
import water.util.ArrayUtils;
import water.util.MathUtils;
import water.webserver.iface.H2OHttpConfig;
import water.webserver.iface.LoginType;

import java.net.InetAddress;

import static water.TestUtil.ari;
import static org.junit.Assert.*;

/**
 * Test to verify correctness of network algebra.
 */
public class NetworkInitTest {

  // Test for H2OKey
  @Test public void testIPv6AddressEncoding() {
    byte[] address = toByte(toOctects(ari(0x2001, 0xdb8, 0x1234, 0x0001, 0x9988, 0x7766, 0xdead, 0xbabe)));
    long high = ArrayUtils.encodeAsLong(address, 8, 8);
    long low = ArrayUtils.encodeAsLong(address, 0, 8);
    AutoBuffer ab = new AutoBuffer();
    byte[] returnedAddress = ab.put8(low).put8(high).flipForReading().getA1(16);
    assertArrayEquals(address, returnedAddress);
  }

  // Test for H2OKey
  @Test public void testIPv4AddressEncoding() {
    byte[] address = toByte(ari(10, 10, 1, 44));
    int ipv4 = (int) ArrayUtils.encodeAsLong(address);
    AutoBuffer ab = new AutoBuffer();
    byte[] returnedAddress = ab.put4(ipv4).flipForReading().getA1(4);
    assertArrayEquals(address, returnedAddress);
  }

  @Test public void testUnsignedOps() {
    assertEquals(-1, MathUtils.compareUnsigned(0x00L, 0xFFL));
    assertEquals(-1, MathUtils.compareUnsigned(0xFFFFFFFFFFFFFFFEL, 0xFFFFFFFFFFFFFFFFL));
    assertEquals(-1, MathUtils.compareUnsigned(0xFFFFFFFFFFFFFFFFL & ~0x80FFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL));
    assertEquals( 0, MathUtils.compareUnsigned(0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL));
    assertEquals(-1, MathUtils.compareUnsigned(0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFEL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL));
    assertEquals(-1, MathUtils.compareUnsigned(0x00L, 0xFFFFFFFFFFFFFFFFL, 0x01L, 0xFFFFFFFFFFFFFFFFL));
    assertEquals(-1, MathUtils.compareUnsigned(0x00L, 0x00L, 0x00L, 0x01L));
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
    expected.ensure_daemon_threads = false;
    
    Assert.assertEquals(expected, cfg);

    // check that setting embedded flag turns on daemon threads
    args.embedded = true;
    expected.ensure_daemon_threads = true;

    Assert.assertTrue(NetworkInit.webServerConfig(args).ensure_daemon_threads);
  }

  @Test
  public void testGetJksAlias() throws Exception {
    assertNull(NetworkInit.getJksAlias(new H2O.OptArgs()));

    H2O.OptArgs oa0 = new H2O.OptArgs();
    oa0.jks_alias = "oa0_alias";
    assertEquals("oa0_alias", NetworkInit.getJksAlias(oa0));

    H2O.OptArgs oa1 = new H2O.OptArgs();
    oa1.hostname_as_jks_alias = true;
    oa1.ip = "oa1_hostname";
    assertEquals("oa1_hostname", NetworkInit.getJksAlias(oa1));

    H2O.OptArgs oa2 = new H2O.OptArgs();
    oa2.hostname_as_jks_alias = true;
    InetAddress addr = InetAddress.getLocalHost();
    String hostname = HostnameGuesser.localAddressToHostname(addr);
    assertEquals(hostname, NetworkInit.getJksAlias(oa2, addr));
  }

  @Test
  public void testWebServerConfig_jksAlias() {
    H2O.OptArgs oa = new H2O.OptArgs();
    oa.hostname_as_jks_alias = true;
    oa.ip = "MyHostName";

    H2OHttpConfig config = NetworkInit.webServerConfig(oa);
    assertEquals("MyHostName", config.jks_alias);
  }
}
