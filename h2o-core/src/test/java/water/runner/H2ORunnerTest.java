package water.runner;

import hex.FrameSplitter;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.rapids.Rapids;
import water.rapids.Val;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class H2ORunnerTest {
    
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    static Frame _covtype;
    static Frame _train;
    static Frame _test;
    double _tol = 1e-10;
    
    @BeforeClass
    public static void beforeClas(){
        _covtype = TestUtil.parse_test_file("/home/pavel/covtype.20k.data");
        _covtype.replace(_covtype.numCols()-1,_covtype.lastVec().toCategoricalVec()).remove();
        Key[] keys = new Key[]{Key.make("train"),Key.make("test")};
        H2O.submitTask(new FrameSplitter(_covtype, new double[]{.8},keys,null)).join();
        _train = DKV.getGet(keys[0]);
        _test = DKV.getGet(keys[1]);
    }
    
    
    @Test
    public void test(){
        System.out.println("Test ran");
    }

    @Test
    @Ignore
    public void test1() throws InterruptedException {
        Frame fr = null;
        Frame rapidsFrame = null;
        Key key = null;
        try {
            key = Key.make("123STARTSWITHDIGITS");
            fr = TestUtil.parse_test_file(key, "smalldata/logreg/prostate.csv");
            Val val = Rapids.exec("(cols_py 123STARTSWITHDIGITS 'ID')");
            assertNotNull(val);
            assertTrue(val.isFrame());
            rapidsFrame = val.getFrame();
        } finally {
        }
    }

    @Test
    @Ignore
    public void test2(){

        Frame fr = null;
        Frame rapidsFrame = null;
        Key key = null;
        try {
            key = Key.make("123STARTSWITHDIGITS");
            fr = TestUtil.parse_test_file(key, "smalldata/logreg/prostate.csv");
            Val val = Rapids.exec("(cols_py 123STARTSWITHDIGITS 'ID')");
            assertNotNull(val);
            assertTrue(val.isFrame());
            rapidsFrame = val.getFrame();
        } finally {
            fr.remove();
            rapidsFrame.remove();
        }
    }
}
