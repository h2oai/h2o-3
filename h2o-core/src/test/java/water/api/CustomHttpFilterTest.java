package water.api;


import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.TestUtil;
import water.init.NetworkInit;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class CustomHttpFilterTest extends TestUtil {
  @BeforeClass static public void setup() {
    stall_till_cloudsize(1);
    // h2o-core/.., register the web bits (so we don't get errs below)
    String relativeResourcePath = System.getProperty("user.dir")+ "/..";
    H2O.registerResourceRoot(new File(relativeResourcePath + File.separator + "h2o-web/src/main/resources/www"));
    H2O.registerResourceRoot(new File(relativeResourcePath + File.separator + "h2o-core/src/main/resources/www"));
    H2O.startServingRestApi();  // calls jetty.acceptRequests
  }

  @Test public void testNoLog() throws Exception {
    
  }
}
