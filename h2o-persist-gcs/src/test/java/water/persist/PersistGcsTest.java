package water.persist;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Chunk;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.util.FileUtils;

import java.net.URI;

import static junit.framework.TestCase.assertEquals;

public class PersistGcsTest extends TestUtil {
  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private static class XORTask extends MRTask<XORTask> {
    long _res = 0;

    @Override
    public void map(Chunk c) {
      for (int i = 0; i < c._len; ++i)
        _res ^= c.at8(i);
    }

    @Override
    public void reduce(XORTask xort) {
      _res = _res ^ xort._res;
    }
  }

  @Test
  public void testGcsImport() throws Exception {
    Scope.enter();
    try {
      Key remoteKey = H2O.getPM().anyURIToKey(new URI("gs://gcp-public-data-nexrad-l2/2018/01/01/KABR/NWS_NEXRAD_NXL2DPBL_KABR_20180101050000_20180101055959.tar"));
      Frame remoteFrame = DKV.getGet(remoteKey);
      FileVec remoteFileVec = (FileVec) remoteFrame.anyVec();

      Key localKey = H2O.getPM().anyURIToKey(new URI(FileUtils.getFile("smalldata/nexrad/NWS_NEXRAD_NXL2DPBL_KABR_20180101050000_20180101055959.tar").getAbsolutePath()));
      FileVec localFileVec = DKV.getGet(localKey);
      assertEquals(localFileVec.length(), remoteFileVec.length());
      assertVecEquals(localFileVec, remoteFileVec, 0);

      int chunkSize = (int) (remoteFileVec.length());
      remoteFileVec.setChunkSize(remoteFrame, chunkSize);
      localFileVec.setChunkSize(chunkSize);
      long remoteXor = new XORTask().doAll(remoteFileVec)._res;
      long localXor = new XORTask().doAll(localFileVec)._res;
      assertEquals(localXor, remoteXor);

      remoteFrame.delete();
      localFileVec.remove();
    } finally {
      Scope.exit();
    }
  }
}
