package hex.tree.gbm;

import hex.Model;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.Log;

import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class GBMEncodingTest extends TestUtil {

    @BeforeClass public static void stall() { stall_till_cloudsize(1); }

    @Parameterized.Parameters
    public static Iterable<?> data() {
        return Arrays.asList(Model.Parameters.CategoricalEncodingScheme.values());
    }

    @Parameterized.Parameter
    public Model.Parameters.CategoricalEncodingScheme encoding;

    @Test public void testGBM_BasicCategoricalEncoding() {
        if (encoding == Model.Parameters.CategoricalEncodingScheme.OneHotInternal) return; //not supported for Tree algos
        Log.info("Using encoding "+encoding);

        try {
            Scope.enter();
            final Frame train = new TestFrameBuilder()
                    .withName("trainEncoding")
                    .withColNames("ColA", "Response")
                    .withVecTypes(Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ar("B", "B", "A", "A", "A"))
                    .withDataForCol(1, ar("C", "C", "V", "V", "V"))
                    .withDomain(0, ar("B", "A"))  //XXX: red flag, this test fails for Eigen encoding if the domain is sorted in lexicographical order (which is H2O-3 default...)
                    .build();
            String target = "Response";

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._seed = 1;
            parms._train = train._key;
            parms._response_column = target;
            parms._ntrees = 1;
            parms._max_depth = 1;
            parms._learn_rate = 1;
            parms._min_rows = 1;
            parms._categorical_encoding = encoding;
            if (encoding == Model.Parameters.CategoricalEncodingScheme.EnumLimited) {
                parms._max_categorical_levels = 2;
            }

            GBM job = new GBM(parms);
            GBMModel gbm = job.trainModel().get();
            Scope.track_generic(gbm);

            Frame trainPreds = gbm.score(train);
            Scope.track(trainPreds);
            assertStringVecEquals(train.vec(target), trainPreds.vec(0));

            final Frame test = new TestFrameBuilder()
                    .withName("testEncoding")
                    .withColNames("ColA")
                    .withVecTypes(Vec.T_CAT)
                    .withDataForCol(0, ar("A"))
                    .build();

            final Frame testPreds = gbm.score(test);
            Scope.track(testPreds);

            assertEquals("V", testPreds.vec(0).stringAt(0));
        } finally {
            Scope.exit();
        }

    }

    @Test public void testGBM_CategoricalEncodingWithUnseenCategories() {
        if (encoding == Model.Parameters.CategoricalEncodingScheme.OneHotInternal) return; //not supported for Tree algos
        Log.info("Using encoding "+encoding);

        try {
            Scope.enter();
            final Frame train = new TestFrameBuilder()
                    .withName("trainEncoding")
                    .withColNames("ColA", "Response")
                    .withVecTypes(Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ar("B", "B", "A", "A", "A", "B", "A"))
                    .withDataForCol(1, ar("C", "C", "V", "V", "V", "C", "V"))
                    .build();
            String target = "Response";

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._seed = 1;
            parms._train = train._key;
            parms._response_column = target;
            parms._ntrees = 1;
            parms._max_depth = 3;
            parms._learn_rate = 1;
            parms._min_rows = 1;
            parms._categorical_encoding = encoding;
            if (encoding == Model.Parameters.CategoricalEncodingScheme.EnumLimited) {
                parms._max_categorical_levels = 2;
            }

            GBM job = new GBM(parms);
            GBMModel gbm = job.trainModel().get();
            Scope.track_generic(gbm);

            Frame trainPreds = gbm.score(train);
            Scope.track(trainPreds);

            final Frame test = new TestFrameBuilder()
                    .withName("testEncoding")
                    .withColNames("ColA")
                    .withVecTypes(Vec.T_CAT)
                    .withDataForCol(0, ar("A", "D", "E"))
                    .build();

            final Frame testPreds = gbm.score(test);
            Scope.track(testPreds);

            assertEquals("V", testPreds.vec(0).stringAt(0));

        } finally {
            Scope.exit();
        }
    }

    @Test public void testGBM_CategoricalEncodingWithPredictionsOnFeaturesSubset() {
        if (encoding == Model.Parameters.CategoricalEncodingScheme.OneHotInternal) return; //not supported for Tree algos
        Log.info("Using encoding "+encoding);

        try {
            Scope.enter();
            final Frame train = new TestFrameBuilder()
                    .withName("trainEncoding")
                    .withColNames("ColA", "ColB", "Response")
                    .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                    .withDataForCol(0, ar("B", "B", "A", "A", "A", "B", "A"))
                    .withDataForCol(1, ar(2, 2, 1, 1, 1, 2, 1))
                    .withDataForCol(2, ar("C", "C", "V", "V", "V", "C", "V"))
                    .withDomain(0, ar("B", "A"))  //XXX: red flag, this test fails for Eigen encoding if the domain is sorted in lexicographical order (which is H2O-3 default...)
                    .build();
            String target = "Response";

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._seed = 1;
            parms._train = train._key;
            parms._response_column = target;
            parms._ntrees = 1;
            parms._max_depth = 3;
            parms._learn_rate = 1;
            parms._min_rows = 1;
            parms._categorical_encoding = encoding;
            if (encoding == Model.Parameters.CategoricalEncodingScheme.EnumLimited) {
                parms._max_categorical_levels = 2;
            }

            GBM job = new GBM(parms);
            GBMModel gbm = job.trainModel().get();
            Scope.track_generic(gbm);

            Frame trainPreds = gbm.score(train);
            Scope.track(trainPreds);

            final Frame test_cat = new TestFrameBuilder()
                    .withName("testEncodingCat")
                    .withColNames("ColA", "ColZ")
                    .withVecTypes(Vec.T_CAT, Vec.T_NUM)
                    .withDataForCol(0, ar("A"))
                    .withDataForCol(1, ard(1/3))
                    .build();

            final Frame testPreds = gbm.score(test_cat);
            Scope.track(testPreds);
            assertEquals("V", testPreds.vec(0).stringAt(0));

            final Frame test_num = new TestFrameBuilder()
                    .withName("testEncodingNum")
                    .withColNames("ColB")
                    .withVecTypes(Vec.T_NUM)
                    .withDataForCol(0, ar(1))
                    .build();

            final Frame testPreds2 = gbm.score(test_num);
            Scope.track(testPreds2);
            assertEquals("V", testPreds2.vec(0).stringAt(0));

            final Frame test_no_common = new TestFrameBuilder()
                    .withName("testEncodingNoCommon")
                    .withColNames("ColZ")
                    .withVecTypes(Vec.T_NUM)
                    .withDataForCol(0, ar(1))
                    .build();

            try {
                Scope.track(gbm.score(test_no_common));
                fail("Should have thrown IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertTrue("Expected exception due to no column in common with training data, but got: "+e.getMessage(),
                        e.getMessage().contains("no columns in common"));
            }

        } finally {
            Scope.exit();
        }
    }
}
