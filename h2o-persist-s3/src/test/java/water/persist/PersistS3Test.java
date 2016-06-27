package water.persist;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.api.ImportFilesHandler;
import water.fvec.Chunk;
import water.fvec.FileVec;
import water.fvec.Frame;

import java.net.URI;
import java.net.URISyntaxException;

import static junit.framework.TestCase.assertEquals;

/**
 * Created by tomas on 6/27/16.
 */
public class PersistS3Test extends TestUtil {


  @BeforeClass
  public static void setup() { stall_till_cloudsize(5); }

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
      Key k2 = H2O.getPM().anyURIToKey(new URI(find_test_file("smalldata/airlines/AirlinesTrain.csv.zip").getAbsolutePath()));
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
}
