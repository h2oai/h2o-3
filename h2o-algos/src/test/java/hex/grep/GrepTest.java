package hex.grep;

import org.junit.*;
import water.DKV;
import water.Job;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.util.FileUtils;

import java.io.File;
import static org.junit.Assert.*;

public class GrepTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testIris() {
    GrepModel kmm = null;
    Frame fr = null;
    try {
      //TODO: fix with original regex
      //String regex = "Iris-versicolor";
      String regex = "ver..c\\wl[ob]r";
      NFSFileVec nfs = TestUtil.makeNfsFileVec("smalldata/iris/iris_wheader.csv");

      DKV.put(fr = new Frame(Key.<Frame>make(), new String[]{"text"}, new Vec[]{nfs}));
//      long now = System.nanoTime();
      GrepModel.GrepParameters parms = new GrepModel.GrepParameters();
      parms._train = fr._key;
      parms._regex = regex;

      Job<GrepModel> job = new Grep(parms).trainModel();
      kmm = job.get();

//      final long dt = System.nanoTime() - now;
//      System.out.println(dt);
      String[] matches = kmm._output._matches;
      assertEquals("Number of matches", 50, matches.length);

      for (int i = 0; i < matches.length; i++) {
        assertEquals("Wrong @" + i, "versicolor", matches[i]);
      }
      job.remove();

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }
}
