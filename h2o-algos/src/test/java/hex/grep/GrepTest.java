package hex.grep;

import org.junit.*;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;

import java.io.File;

public class GrepTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  
  @Test public void testIris() {
    GrepModel kmm = null;
    Frame fr = null;
    try {
      String regex = "Iris-versicolor";
      File f = new File("smalldata/iris/iris_wheader.csv");
      //String regex = "(?:(\\w)\\1){5}";
      //File f = new File("bigdata/laptop/text8.txt");
      NFSFileVec nfs = NFSFileVec.make(f);
      DKV.put(fr = new Frame(Key.make(), new String[]{"text"}, new Vec[]{nfs}));

      GrepModel.GrepParameters parms = new GrepModel.GrepParameters();
      parms._train = fr._key;
      parms._regex = regex;

      Grep job = new Grep(parms).trainModel();
      kmm = job.get();
      job.remove();

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }
}
