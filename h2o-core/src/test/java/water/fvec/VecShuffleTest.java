package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.util.VecUtils;


public class VecShuffleTest extends TestUtil{


    @BeforeClass
    public static void stall()      { stall_till_cloudsize(1); }

    /**
     * Create a frame with different Vec types to test MRTask for each type 
     */
    private Frame makeTestFrame() {
        Frame fr = null;
        Vec v = ivec(1, 2, 3, 4, 5);
        try {
            fr = new MRTask() {
                @Override
                public void map(Chunk[] cs, NewChunk[] ncs) {
                    for (int i = 0; i < cs[0]._len; i++) {
                        int r = (int) cs[0].atd(i);
                        assert r != 0;
                        ncs[0].addNum(r);
                        ncs[1].addNum(11.2 * r);
                        ncs[2].addUUID(r, r * 10);
                        ncs[3].addStr("row" + r);
                        ncs[4].addCategorical(r % 2 == 0 ? 0 : 1);
                    }
                }
            }.doAll(new byte[]{Vec.T_NUM, Vec.T_NUM, Vec.T_UUID, Vec.T_STR, Vec.T_CAT}, v).outputFrame(Key.make("data"),
                    new String[]{"v1", "v2", "v3", "v4", "v5"},
                    new String[][]{null, null, null, null, new String[]{"a", "b"}});
        } finally {
            v.remove();
        }
        assert fr != null;
        return fr;
    }

    /**
     * Test shuffling algorithm.
     * Shuffles a Vec with different Vec types making sure all elements are there.
     */
    @Test
    public void testShuffleVec(){
        Frame fr = null;
        try {
            Scope.enter();
            fr = makeTestFrame();
            for (int i = 0 ; i < 5 ; i++) {
                // Get shuffled Vec
                Vec shuffledFeature = VecUtils.shuffleVec(fr.vec(i), 0);
                Assert.assertFalse(checkElements(fr.vec(i), shuffledFeature));
            }
        } finally {
            if (fr != null) fr.remove();
            Scope.exit();
        }
    }

    /**
     * Compares two vectors making sure the shuffling works and doesnt `lose` elements 
     */
    public boolean checkElements(Vec src, Vec shuffled){
        boolean seen = false;
        boolean elementMissing = false;
        for (long i = 0; i < src.length(); ++i) {
            for (long j = 0; j < shuffled.length(); ++j){
                if (src._type == Vec.T_UUID) {
                    if (shuffled.at16l(j) == src.at16l(i)) {
                        seen = true;
                        continue;
                    }
                }
                else if (src._type == Vec.T_STR) {
                    if (shuffled.stringAt(j).equals(src.stringAt(i))) {
                        seen = true;
                        continue;
                    }
                }
                else if (shuffled.at(j) == src.at(i)){
                    seen = true;
                    continue;
                }
                // If element not seen before and its the last position, element is missing
                if (!seen && j == shuffled.length()){
                    elementMissing = true;
                }
            }
            seen = false;
        }
        return elementMissing;
    }

    /**
     * Compare values of Vec and returns the randomness change 
     * (0.0 same vector, 1.0 perfect shuffle)
     */
    double randomnessCoefficient(Vec og, Vec nw) {
        int changedPlaces = 0;
        switch (og.get_type()) {
            case Vec.T_STR:
                for (long i = 0; i < nw.length(); ++i) {
                    if (!og.stringAt(i).equals(nw.stringAt(i)))
                        changedPlaces++;
                }
                break;
            case Vec.T_UUID:
                for (long i = 0; i < nw.length(); ++i) {
                    if (og.at16l(i) != nw.at16l(i))
                        changedPlaces++;
                }
                break;
            case Vec.T_NUM: /* fallthrough */
            case Vec.T_CAT:
            case Vec.T_TIME:
            default:

                for (long i = 0; i < nw.length(); ++i) {
                    double l = og.at(i);
                    double r = nw.at(i);
                    if (og.at(i) != nw.at(i))
                        changedPlaces++;
                }
                break;
        }
        return (changedPlaces * 1.0) / nw.length();
    }


    /**
     * Testing algorithm multiple times containing Int and String
     */
    @Test
    public void shuffleSmallFrame(){
        Frame fr = null;
        try {
            Scope.enter();
            fr = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_STR)
                    .withColNames("first", "second")
                    .withDataForCol(0, ard(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0))
                    .withDataForCol(1, ar("a", "b", "c", "d", "e", "f","g", "h", "i", "j", "k"))
                    .build();
            Scope.track(fr);
            DKV.put(fr._key, fr);

            for (int i = 0 ; i < 10 ; i++) {
                for (int j = 0; j < 2 ; j++){
                    // Get shuffled Vec
                    Vec shuffledFeature = VecUtils.shuffleVec(fr.vec(j), 0);
                    double rndCoe = randomnessCoefficient(fr.vec(j), shuffledFeature);
                    Assert.assertEquals(1.0, rndCoe, 1e-2);
                    Assert.assertFalse(checkElements(fr.vec(j), shuffledFeature));
                }
            }
        } finally {
            Scope.exit();
            if (fr != null) fr.remove();
        }
    }

    /**
     * Test Bigger frame with unique Double values
     */
    @Test
    public void shuffleBigData(){
        Frame fr = null;
        Vec shuffledFeature = null ;
        try {
            Scope.enter();
            fr = parse_test_file("bigdata/laptop/lending-club/loan.csv");
            Scope.track(fr);
            DKV.put(fr);
            for (int i = 0 ; i < fr.numCols() ; i++) {
                // get the shuffled vec
                shuffledFeature = VecUtils.shuffleVec(fr.vec(i),  0);
                double rndCoe = randomnessCoefficient(fr.vec(i), shuffledFeature);
                Assert.assertNotEquals(0.0, rndCoe);
            }

        } finally {
            Scope.exit();
            if (shuffledFeature != null) shuffledFeature.remove();
            if (fr != null) fr.remove();
        }
    }

    /**
     * Test Bigger frame with unique String values
     */
    @Test
    public void shuffleString(){
        Frame fr = null;
        try {
            Scope.enter();
            // testing on 30,000 unique strings
            fr = parse_test_file("smalldata/gbm_test/30k_cattest.csv");
            assert fr.vec(0).isString();

            Vec shuffledFeature = VecUtils.shuffleVec(fr.vec(0), 0);
            Assert.assertFalse(checkElements(fr.vec(0), shuffledFeature));
        } finally {
            Scope.exit();
            if (fr != null) fr.remove();
        }
    }

    /**
     * Test frame which MRTask will be used on PermutationVarImp
     */
    @Test
    public void frameShuffleTest() {
        Scope.enter();
        Key parsed = Key.make("cars_parsed");
        Frame fr = null;
        try {
            fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
            Scope.track(fr);
            DKV.put(fr);
            for (int i = 0; i < fr.numCols(); i++) {
                Vec shuffledFeature = VecUtils.shuffleVec(fr.vec(i), 0);
                // Testing only elements. Frame has multiple not unique variables which leads no low Random Coefficient
                Assert.assertFalse(checkElements(fr.vec(i), shuffledFeature));
            }
        } finally {
            Scope.exit();
            if (fr != null) fr.remove();
        }
    }
}
