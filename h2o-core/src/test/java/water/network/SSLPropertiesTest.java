package water.network;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class SSLPropertiesTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void expandPath() throws IOException  {
    final File absPath = tmp.newFile().getAbsoluteFile();

    SSLProperties noPathProps = new SSLProperties();
    assertNull(noPathProps.expandPath(null));
    assertEquals("test/path", noPathProps.expandPath("test/path"));
    assertEquals(absPath.getAbsolutePath(), noPathProps.expandPath(absPath.getAbsolutePath()));

    noPathProps.setProperty("h2o_ssl_jks_internal", "internal.jks");
    assertEquals("internal.jks", noPathProps.h2o_ssl_jks_internal());

    assertEquals("internal.jks", noPathProps.h2o_ssl_jts());

    noPathProps.setProperty("h2o_ssl_jts", "internal.jts");
    assertEquals("internal.jts", noPathProps.h2o_ssl_jts());
  }

  @Test
  public void expandPathRelative() throws IOException  {
    final File absPath = tmp.newFile().getAbsoluteFile();
    final File confDir = tmp.newFolder("conf");

    SSLProperties noPathProps = new SSLProperties(confDir);
    assertNull(noPathProps.expandPath(null));
    assertEquals(new File(confDir,"test/path").getAbsolutePath(), noPathProps.expandPath("test/path"));
    assertEquals(absPath.getAbsolutePath(), noPathProps.expandPath(absPath.getAbsolutePath()));

    noPathProps.setProperty("h2o_ssl_jks_internal", "internal.jks");
    assertEquals(new File(confDir, "internal.jks").getAbsolutePath(), noPathProps.h2o_ssl_jks_internal());

    assertEquals(new File(confDir, "internal.jks").getAbsolutePath(), noPathProps.h2o_ssl_jts());

    noPathProps.setProperty("h2o_ssl_jts", "internal.jts");
    assertEquals(new File(confDir, "internal.jts").getAbsolutePath(), noPathProps.h2o_ssl_jts());
  }

}
