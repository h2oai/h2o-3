package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.util.VecUtils;


public class VecShuffleTest extends TestUtil{


    @BeforeClass
    public static void stall() {
        stall_till_cloudsize(1);
    }


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

    // Test just runs through all Vec types, shuffing testing on next test

    @Test
    public void test_shuffle_vec(){
        Frame fr = makeTestFrame();
        assert fr != null;
        for (int i = 0 ; i < 5 ; i++) {
            Vec shuffled_feature = VecUtils.ShuffleVec(fr.vec(i), fr.vec(i).makeCopy());
        }
    }

    // Simple dataset for testing shuffling
    @Test
    public void shuffleSmallDoubleVec(){
        Frame fr = null;
        Vec shuffled_feature = null;
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
                    shuffled_feature = VecUtils.ShuffleVec(fr.vec(j), fr.vec(j).makeCopy());
                    double rnd_coe = randomnessCoeefic(fr.vec(j), shuffled_feature);
                    Assert.assertEquals(1.0, rnd_coe, 1e-2);

                }
            }

        } finally {
            Scope.exit();
            shuffled_feature.remove();
            fr.remove();
        }

    }

    // compares values of vec and returns the randomness change. 0 no change, 1.0 all rows shuffled
    double randomnessCoeefic(Vec og, Vec nw) {
        int changed_places = 0;
        switch (og.get_type()) {
            case Vec.T_STR:
                for (int i = 0; i < nw.length(); ++i) {
                    if (!og.stringAt(i).equals(nw.stringAt(i)))
                        changed_places++;
                }
                break;
            case Vec.T_NUM:
            case Vec.T_CAT:
            case Vec.T_TIME:
                for (int i = 0; i < nw.length(); ++i) {
                    double l = og.at(i);
                    double r = nw.at(i);
                    if (og.at(i) != nw.at(i))
                        changed_places++;
                }
                break;
            case Vec.T_UUID:
                for (int i = 0; i < nw.length(); ++i) {
                    if (og.at16l(i) != nw.at16l(i))
                        changed_places++;
                }
                break;
            default:
                throw new IllegalArgumentException("type not supported, FIX!");

        }
        return changed_places * 1.0 / nw.length();
    }

    @Test
    public void shuffleBigVec(){
        Frame fr = null;
        Vec shuffled_feature = null ;
        try {
            Scope.enter();
            fr = parse_test_file("./smalldata/jira/shuffle_test_2.csv"); // has 10000000 unique values;  
            Scope.track(fr);
            
            for (int i = 0 ; i < 10 ; i++) {
                shuffled_feature = VecUtils.ShuffleVec(fr.vec(0), fr.vec(0).makeCopy());
                double rnd_coe = randomnessCoeefic(fr.vec(0), shuffled_feature);
                Assert.assertEquals(1.0, rnd_coe, 1e-2);
            }

        } finally {
            Scope.exit();
            shuffled_feature.remove();
            fr.remove();
        }
    }
}
