package water.rapids;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.*;

public class GroupByLargeTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testSmallText() {
    System.out.println("Running small GroupBy ...");
    NFSFileVec nfs = NFSFileVec.make(find_test_file("smalldata/glm_test/citibike_small_train.csv"));
    Frame fr = new Frame(Key.make("raw_text"), new String[]{"text"}, new Vec[]{nfs});
    DKV.put(fr);
    String rapids = "(GB raw_text [0] nrow 0 \"all\")";

    //ASTGroup._testing_force_sorted = true;
    Val val = Exec.exec(rapids);
    ASTGroup._testing_force_sorted = false;
    fr.delete();
    System.out.println(val.toString());
    Frame res = val.getFrame();
    Assert.assertEquals( 2,res.numCols());
    Assert.assertEquals(67,res.numRows());

    chkFr(res, 0,10, 6240); // Group ASCII '\n'
    chkFr(res, 1,32,27693); // Group ASCII ' '
    chkFr(res, 2,34,39056); // Group ASCII '"'
    chkFr(res, 9,48,92388); // Group ASCII '0'
    chkFr(res,19,65, 3937); // Group ASCII 'A'

    res.delete();
  }

  private void chkFr( Frame fr, int row, long exp0, long exp1) {
    Assert.assertEquals(exp0, fr.vec(0).at8(row)); 
    Assert.assertEquals(exp1, fr.vec(1).at8(row)); 
  }
}
