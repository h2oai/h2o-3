package water.persist;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Chunk;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.util.FileUtils;

import java.net.URI;
import java.util.ArrayList;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Created by tomas on 6/27/16.
 */
public class PersistS3Test extends TestUtil {
  private static final String AWS_ACCESS_KEY_PROPERTY_NAME = "AWS_ACCESS_KEY_ID";
  private static final String AWS_SECRET_KEY_PROPERTY_NAME = "AWS_SECRET_ACCESS_KEY";

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  private static class XORTask extends MRTask<XORTask> {
    long _res = 0;

    @Override public void map(Chunk c) {
      for(int i = 0; i < c._len; ++i)
        _res ^= c.at8(i);
    }
    @Override public void reduce(XORTask xort){
      _res = _res ^ xort._res;
    }
  }
  @Test
  public void testS3Import() throws Exception {
    Scope.enter();
    try {
      Key k = H2O.getPM().anyURIToKey(new URI("s3://h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip"));
      Frame fr = DKV.getGet(k);
      FileVec v = (FileVec) fr.anyVec();
      // make sure we have some chunks
      int chunkSize = (int) (v.length() / 3);
      v.setChunkSize(fr, chunkSize);
      long xor = new XORTask().doAll(v)._res;
      Key k2 = H2O.getPM().anyURIToKey(new URI(FileUtils.getFile("smalldata/airlines/AirlinesTrain.csv.zip").getAbsolutePath()));
      FileVec v2 = DKV.getGet(k2);
      assertEquals(v2.length(), v.length());
      assertVecEquals(v, v2, 0);
      // make sure we have some chunks
      v2.setChunkSize(chunkSize);
      long xor2 = new XORTask().doAll(v2)._res;
      assertEquals(xor2, xor);
      fr.delete();
      v2.remove();
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testS3UriToKeyUriCredentials() throws Exception {
    // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
    final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
    final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

    assumeTrue(accessKey != null);
    assumeTrue(secretKey != null);

    StringBuilder s3UrlBuilder = new StringBuilder("s3://");
    s3UrlBuilder.append(accessKey);
    s3UrlBuilder.append(":");
    s3UrlBuilder.append(secretKey);
    s3UrlBuilder.append("@test.0xdata.com/h2o-unit-tests/iris.csv");

    Scope.enter();
    Key k = null, k2 = null;
    Frame fr = null;
    FileVec v = null, v2 = null;
    try {
      k = H2O.getPM().anyURIToKey(new URI(s3UrlBuilder.toString()));
      fr = DKV.getGet(k);
      v = (FileVec) fr.anyVec();
      // make sure we have some chunks
      int chunkSize = (int) (v.length() / 3);
      v.setChunkSize(fr, chunkSize);
      long xor = new XORTask().doAll(v)._res;
      k2 = H2O.getPM().anyURIToKey(new URI(FileUtils.getFile("smalldata/iris/iris.csv").getAbsolutePath()));
      v2 = DKV.getGet(k2);
      assertEquals(v2.length(), v.length());
      assertVecEquals(v, v2, 0);
      // make sure we have some chunks
      v2.setChunkSize(chunkSize);
      long xor2 = new XORTask().doAll(v2)._res;
      assertEquals(xor2, xor);
      fr.delete();
      v2.remove();
    } finally {
      Scope.exit();
      if (k != null) k.remove();
      if (k2 != null) k2.remove();
      if (fr != null) fr.remove();
      if (v != null) v.remove();
      if (v2 != null) v2.remove();
    }
  }

  @Test
  public void testS3ImportUriCredentials() throws Exception {
    // This test is only runnable in environment with Amazon credentials properly set {AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY}
    final String accessKey = System.getenv(AWS_ACCESS_KEY_PROPERTY_NAME);
    final String secretKey = System.getenv(AWS_SECRET_KEY_PROPERTY_NAME);

    assumeTrue(accessKey != null);
    assumeTrue(secretKey != null);

    StringBuilder s3UrlBuilder = new StringBuilder("s3://");
    s3UrlBuilder.append(accessKey);
    s3UrlBuilder.append(":");
    s3UrlBuilder.append(secretKey);
    s3UrlBuilder.append("@test.0xdata.com/h2o-unit-tests/iris.csv");

    PersistS3 persistS3 = new PersistS3();
    final ArrayList<String> keys = new ArrayList<>();
    final ArrayList<String> fails = new ArrayList<>();
    final ArrayList<String> deletions = new ArrayList<>();
    final ArrayList<String> files = new ArrayList<>();

    try {
      persistS3.importFiles(s3UrlBuilder.toString(), null, files, keys, fails, deletions);
      assertEquals(0, fails.size());
      assertEquals(0, deletions.size());
      assertEquals(1, files.size());
      assertEquals(1, keys.size());
    } finally {
      for (String key : keys){
        final Iced iced = DKV.getGet(key);
        assertTrue(iced instanceof Frame);
        final Frame frame = (Frame) iced;
        frame.remove();
      }
    }
    
  }
}
