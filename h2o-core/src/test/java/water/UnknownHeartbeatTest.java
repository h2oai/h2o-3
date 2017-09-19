package water;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;

import static org.junit.Assert.assertEquals;

public class UnknownHeartbeatTest extends TestUtil{
  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testIgnoreUnknownHeartBeat() throws IOException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    HeartBeat hb = new HeartBeat();
    hb._cloud_name_hash = 777;
    hb._client = true;
    hb._jar_md5 = H2O.SELF._heartbeat._jar_md5;

    AutoBuffer ab = new AutoBuffer(H2O.SELF,H2O.MAX_PRIORITY);

    Method putSpMethod = AutoBuffer.class.getDeclaredMethod("putSp", int.class);
    putSpMethod.setAccessible(true);
    putSpMethod.invoke(ab, ab._bb.position()+1+2);
    ab._bb.put    ((byte)UDP.udp.heartbeat.ordinal());
    ab._bb.putChar((char)65400);
    hb.write(ab);
    ab.close();

    // Verify that we don't have a new client
    assertEquals(H2O.CLOUD.size(), 1);
    assertEquals(H2O.getClients().size(), 0);
  }
}
