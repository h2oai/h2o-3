package water.rapids.ast.prims.advmath;

import hex.CreateFrame;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.TestUtil;
import water.fvec.Frame;
import water.parser.BufferedString;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.ArrayUtils;


public class StratifiedSplitTest extends TestUtil{
    private static Frame f = null, fr1 = null;

    @BeforeClass public static void setup() {
        stall_till_cloudsize(1);
    }

    @AfterClass public static void teardown() {
        f.delete(); fr1.delete();
    }

    @Test public void testStratifiedSampling() {
        //f = ArrayUtils.frame("response" ,vec(ar("bird","cat","dog"),ari(0,0,1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1)));
        f = ArrayUtils.frame("response" ,vec(ari(1,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1)));
        f = new Frame(f);
        DKV.put(f);
        System.out.println("key f: " + f._key);
        Val res1 = Rapids.exec("(h2o.random_stratified_split " + f._key + " 0.333333333333 123)"); //
        fr1 = res1.getFrame();
        System.out.println("key fr1: " + fr1._key);
        //Assert.assertEquals(fr2.vec(0).atStr(new BufferedString(),0),isaxIDX);
        for (int i = 0; i < fr1.anyVec().length(); i++) {
            System.out.println(fr1.anyVec().at8(i));
        }
    }
}
