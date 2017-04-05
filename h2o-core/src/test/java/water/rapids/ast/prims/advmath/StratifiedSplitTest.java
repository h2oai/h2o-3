package water.rapids.ast.prims.advmath;

import hex.CreateFrame;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.ArrayUtils;


public class StratifiedSplitTest extends TestUtil {

    @BeforeClass public static void setup() {
        stall_till_cloudsize(1);
    }

    @AfterClass public static void teardown() {
    }

    public static Frame frame(String name, Vec vec) {
        final Frame f = ArrayUtils.frame(name, vec);
        f._key = Key.make();
        DKV.put(f);
        return Scope.track(f);
    }

    public static Frame frame(Frame frame) {
        return Scope.track(new Frame(frame));
    }

    @Test public void testStratifiedSampling() {
        Scope.enter();
        Frame f = frame("response" ,vec(ari(1,0,0,0,0,0,0,0,0,0,0,1)));
        Frame fanimal = frame("response" ,vec(ar("dog","cat"),ari(1,0,0,0,0,0,0,0,0,0,0,1)));
        f = frame(f);
        fanimal = frame(fanimal);
        f._key = Key.make();
        fanimal._key = Key.make();
        DKV.put(f);
        DKV.put(fanimal);
        Scope.track(f);
        Scope.track(fanimal);

        Val res1 = Rapids.exec("(h2o.random_stratified_split (cols_py " + f._key + " 0) 0.3333333 123)"); //
        Frame fr1 = Scope.track(res1.getFrame());

        Assert.assertEquals(fr1.vec(0).at8(0),1);  // minority class should be in the test split
        Assert.assertEquals(fr1.vec(0).at8(11),0);  // minority class should be in the train split
        Assert.assertEquals(fr1.vec(0).mean(),1.0/3.0,1e-5);  // minority class should be in the train split
        //test categorical
        Val res2 = Rapids.exec("(h2o.random_stratified_split (cols_py " + fanimal._key + " 0) 0.3333333 123)"); //
        Frame fr2 = Scope.track(res2.getFrame());
        Assert.assertEquals(fr2.vec(0).at8(0),1);  // minority class should be in the test split
        Assert.assertEquals(fr2.vec(0).at8(11),0);  // minority class should be in the test split
        Assert.assertEquals(fr2.vec(0).mean(),1.0/3.0,1e-5);  // minority class should be in the test split
        Scope.exit();

    }

    @Test public void testSplit() {
        Scope.enter();

        Frame f = frame("response" ,vec(ari(1,0,0,0,0,0,0,0,0,0,0,1)));
        Frame fanimal = frame("response" ,vec(ar("dog","cat"),ari(1,0,0,0,0,0,0,0,0,0,0,1)));
        f = frame(f);
        fanimal = frame(fanimal);

        Frame fr1 = Scope.track(AstStratifiedSplit.split(f, f.anyVec(), 0.3333333, 123, new String[]{"hay", "straw"}));
        Assert.assertEquals(fr1.vec(0).at8(0),1);  // minority class should be in the test split
        Assert.assertEquals(fr1.vec(0).at8(11),0);  // minority class should be in the train split
        Assert.assertEquals(fr1.vec(0).mean(),1.0/3.0,1e-5);  // minority class should be in the train split
        //test categorical

        Frame fr2 = Scope.track(AstStratifiedSplit.split(fanimal, fanimal.anyVec(), 0.3333333, 123, new String[]{"cats", "dogs"}));
        Assert.assertEquals(fr2.vec(0).at8(0),1);  // minority class should be in the test split
        Assert.assertEquals(fr2.vec(0).at8(11),0);  // minority class should be in the test split
        Assert.assertEquals(fr2.vec(0).mean(),1.0/3.0,1e-5);  // minority class should be in the test split
        
        Scope.exit();

    }
}
