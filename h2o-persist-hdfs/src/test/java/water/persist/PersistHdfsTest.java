package water.persist;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import static junit.framework.TestCase.*;

@RunWith(Parameterized.class)
public class PersistHdfsTest extends TestUtil {

  @Parameterized.Parameters(name = "{index}: scheme={0}")
  public static Object[] schemes() { 
    return new Object[] { 
      // "s3n", - s3n is not supported by hadoop-aws 3.0+
      "s3a" };
  }

  @Parameterized.Parameter
  public String scheme;

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  private Persist hdfsPersist;

  @Before
  public void initPersist() {
    hdfsPersist = H2O.getPM().getPersistForURI(URI.create("hdfs://localhost/"));
  }

  @Test
  public void testImport() throws Exception {
    Scope.enter();
    try {
      Key keyS3 = hdfsPersist.uriToKey(URI.create(scheme + "://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip"));
      Frame fr = Scope.track((Frame) DKV.getGet(keyS3));
      FileVec vS3 = (FileVec) fr.anyVec();

      Key keyLocal = H2O.getPM().anyURIToKey(new URI(FileUtils.getFile("smalldata/airlines/AirlinesTrain.csv.zip").getAbsolutePath()));
      FileVec vLocal = (FileVec) Scope.track((Vec) DKV.getGet(keyLocal));

      assertEquals(vLocal.length(), vS3.length());
      assertVecEquals(vLocal, vS3, 0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testExist() {
    String existing = scheme + "://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip";
    assertTrue(hdfsPersist.exists(existing));

    String invalid = scheme + "://h2o-public-test-data/smalldata/does.not.exist";
    assertFalse(hdfsPersist.exists(invalid));
  }

  @Test
  public void testGetParent() {
    String directory = "hdfs://h2o-public-test-data/smalldata/airlines";
    assertEquals("hdfs://h2o-public-test-data/smalldata", hdfsPersist.getParent(directory));
  }

  @Test
  public void testIsSeekableOpenSupported() {
    assertTrue(hdfsPersist.isSeekableOpenSupported());
  }
 
  @Test
  public void testWrapSeekable()  {
    try {
      Scope.enter();
      Vec vec = Scope.track(Vec.makeZero(10));
      InputStream seekable = hdfsPersist.wrapSeekable(vec);
      assertTrue(seekable instanceof FSDataInputStream);
      assertTrue(((FSDataInputStream) seekable).getWrappedStream() instanceof VecDataInputStream);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testInjectS3ASecurityCredentialProviders() {
    Configuration conf = new Configuration(false);
    conf.set("fs.s3a.security.credential.provider.path", "dummy");
    PersistHdfs.injectS3ASecurityCredentialProviders(conf);
    final String actualProviders = conf.get("fs.s3a.security.credential.provider.path");
    final String expectedProviders = "dummy,hex://water.persist.H2OCredentialProviderFactory";
    assertEquals(actualProviders, expectedProviders);
  }

  @Test
  public void testDefaultConfigurationContainsCustomProviders() {
    Configuration conf = new Configuration(false);
    PersistHdfs.injectS3ASecurityCredentialProviders(conf);
    final String defaultProviders = PersistHdfs.CONF.get("fs.s3a.security.credential.provider.path");
    final String injectedProviders = conf.get("fs.s3a.security.credential.provider.path");
    assertTrue(defaultProviders.endsWith(injectedProviders));
  }

}
