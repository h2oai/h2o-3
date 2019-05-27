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

import static org.junit.Assert.assertEquals;

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
                    .withName("trainLabelEncoding")
                    .withColNames("ColA", "Response")
                    .withVecTypes(Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ar("B", "B", "A", "A", "A"))
                    .withDataForCol(1, ar("C", "C", "V", "V", "V"))
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
                    .withName("testLabelEncoding")
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
                    .withName("trainLabelEncoding")
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
                    .withName("testLabelEncoding")
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
}
