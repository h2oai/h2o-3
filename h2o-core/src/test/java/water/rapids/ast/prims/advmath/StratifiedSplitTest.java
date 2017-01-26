package water.rapids.ast.prims.advmath;

import hex.CreateFrame;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.parser.BufferedString;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.ArrayUtils;


public class StratifiedSplitTest extends TestUtil{
    private static Frame f = null, fr1 = null, fanimal = null, fr2 = null;

    @BeforeClass public static void setup() {
        stall_till_cloudsize(1);
    }

    @AfterClass public static void teardown() {
        f.delete(); fr1.delete(); fanimal.delete(); fr2.delete();
    }

    @Test public void testStratifiedSampling() {
        f = ArrayUtils.frame("response" ,vec(ari(1,0,0,0,0,0,0,0,0,0,0,1)));
        fanimal = ArrayUtils.frame("response" ,vec(ar("dog","cat"),ari(1,0,0,0,0,0,0,0,0,0,0,1)));
        f = new Frame(f);
        fanimal = new Frame(fanimal);
        f._key = Key.make();
        fanimal._key = Key.make();
        DKV.put(f);
        DKV.put(fanimal);
        Val res1 = Rapids.exec("(h2o.random_stratified_split (cols_py " + f._key + " 0) 0.3333333 222)"); //
        fr1 = res1.getFrame();
        //Assert.assertEquals(fr1.vec(0).at8(0),1);  // minority class should be in the test split
        //Assert.assertEquals(fr1.vec(0).at8(11),0);  // minority class should be in the train split
        for (int i = 0; i < fr1.anyVec().length(); i++) {
            System.out.println(fr1.anyVec().at8(i));
        }
        System.out.println("\n");
        //test categorical
        Val res2 = Rapids.exec("(h2o.random_stratified_split (cols_py " + fanimal._key + " 0) 0.3333333 222)"); //
        fr2 = res2.getFrame();
        for (int i = 0; i < fr2.anyVec().length(); i++) {
            System.out.println(fr2.anyVec().at8(i));
        }
        //Assert.assertEquals(fr2.vec(0).at8(0),1);  // minority class should be in the test split
        //Assert.assertEquals(fr2.vec(0).at8(11),0);  // minority class should be in the test split
    }
}
