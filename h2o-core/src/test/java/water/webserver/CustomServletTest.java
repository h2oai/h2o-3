package water.webserver;

import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.TestUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class CustomServletTest extends TestUtil {

  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testEchoServletWorking() throws Exception {
    URL echoURL = new URL(H2O.getURL(H2O.ARGS.jks != null ? "https" : "http") + "/99/Echo?test-query");
    try (InputStream is = echoURL.openStream()) {
      String result = new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
      assertEquals("test-query", result);
    }
  }

}
