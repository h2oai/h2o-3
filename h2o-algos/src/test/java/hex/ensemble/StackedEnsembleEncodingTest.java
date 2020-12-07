package hex.ensemble;

import hex.Model;
import hex.ensemble.StackedEnsembleModel.StackedEnsembleParameters;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Job;
import water.Lockable;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class StackedEnsembleEncodingTest extends TestUtil {

    @BeforeClass public static void stall() { stall_till_cloudsize(1); }

    @Parameterized.Parameters
    public static Iterable<?> data() {
        return Arrays.asList(Model.Parameters.CategoricalEncodingScheme.values());
//        return Arrays.asList(Model.Parameters.CategoricalEncodingScheme.OneHotExplicit);
    }

    @Parameterized.Parameter
    public Model.Parameters.CategoricalEncodingScheme encoding;

    @Test public void testSE_BasicCategoricalEncoding() {
        if (encoding == Model.Parameters.CategoricalEncodingScheme.OneHotInternal) return; //not supported for Tree algos
        Log.info("Using encoding "+encoding);

        List<Lockable> deletables = new ArrayList<>();
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

            DRFModel.DRFParameters params = new DRFModel.DRFParameters();
            params._train = train._key;
            params._response_column = target;
            params._sample_rate = 1;
            params._min_rows = 1;
            params._seed = 1;
            params._nfolds = 2;
            params._keep_cross_validation_models = false;
            params._keep_cross_validation_predictions = true;
            params._categorical_encoding = encoding;
            if (encoding == Model.Parameters.CategoricalEncodingScheme.EnumLimited) {
                params._max_categorical_levels = 2;
            }

            Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, new HashMap<String, Object[]>() {{
                put("_ntrees", new Integer[]{1, 2});
                put("_max_depth", new Integer[]{2, 3});
            }});
            Grid grid = gridSearch.get(); deletables.add(grid);
            Model[] gridModels = grid.getModels(); deletables.addAll(Arrays.asList(gridModels));
            assertEquals(4, gridModels.length);

            StackedEnsembleParameters seParams = new StackedEnsembleParameters();
            seParams._train = train._key;
            seParams._response_column = target;
            seParams._base_models = ArrayUtils.append(grid.getModelKeys());
            seParams._seed = 1;
            StackedEnsembleModel se = new StackedEnsemble(seParams).trainModel().get(); deletables.add(se);


            Frame trainPreds = se.score(train);
            Scope.track(trainPreds);
            assertStringVecEquals(train.vec(target), trainPreds.vec(0));

            final Frame test = new TestFrameBuilder()
                    .withName("testEncoding")
                    .withColNames("ColA")
                    .withVecTypes(Vec.T_CAT)
                    .withDataForCol(0, ar("A", "B"))
                    .build();

            for (Model model : gridModels) {
                final Frame testPreds = model.score(test);
                Scope.track(testPreds);
                assertEquals("V", testPreds.vec(0).stringAt(0));
            }

            final Frame testPreds = se.score(test);
            Scope.track(testPreds);

            assertEquals("V", testPreds.vec(0).stringAt(0));
            assertEquals("C", testPreds.vec(0).stringAt(1));
        } finally {
            Scope.exit();
            for (Lockable l: deletables) {
                if (l instanceof Model) ((Model)l).deleteCrossValidationPreds();
                l.delete();
            }
        }

    }

    @Test public void testSE_CategoricalEncodingWithUnseenCategories() {
        if (encoding == Model.Parameters.CategoricalEncodingScheme.OneHotInternal) return; //not supported for Tree algos
        Log.info("Using encoding "+encoding);

        List<Lockable> deletables = new ArrayList<>();
        try {
            Scope.enter();
            final Frame train = new TestFrameBuilder()
                    .withName("trainEncoding")
                    .withColNames("ColA", "Response")
                    .withVecTypes(Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ar("B", "B", "A", "A", "A", "B", "A", "E"))
                    .withDataForCol(1, ar("C", "C", "V", "V", "V", "C", "V", "V"))
                    .build();
            String target = "Response";

            DRFModel.DRFParameters params = new DRFModel.DRFParameters();
            params._train = train._key;
            params._response_column = target;
            params._sample_rate = 1;
            params._min_rows = 1;
            params._seed = 1;
            params._nfolds = 2;
            params._keep_cross_validation_models = false;
            params._keep_cross_validation_predictions = true;
            params._categorical_encoding = encoding;
            if (encoding == Model.Parameters.CategoricalEncodingScheme.EnumLimited) {
                params._max_categorical_levels = 2;
            }

            Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, new HashMap<String, Object[]>() {{
                put("_ntrees", new Integer[]{1, 2});
                put("_max_depth", new Integer[]{2, 3});
            }});
            Grid grid = gridSearch.get();
            deletables.add(grid);
            Model[] gridModels = grid.getModels();
            deletables.addAll(Arrays.asList(gridModels));
            assertEquals(4, gridModels.length);

            StackedEnsembleParameters seParams = new StackedEnsembleParameters();
            seParams._train = train._key;
            seParams._response_column = target;
            seParams._base_models = ArrayUtils.append(grid.getModelKeys());
            seParams._seed = 1;
            StackedEnsembleModel se = new StackedEnsemble(seParams).trainModel().get();
            deletables.add(se);

            Frame trainPreds = se.score(train);
            Scope.track(trainPreds);

            final Frame test = new TestFrameBuilder()
                    .withName("testEncoding")
                    .withColNames("ColA")
                    .withVecTypes(Vec.T_CAT)
                    .withDataForCol(0, ar("A", "D", "E"))
                    .build();

            for (Model model : gridModels) {
                final Frame testPreds = model.score(test);
                Scope.track(testPreds);
                assertEquals("V", testPreds.vec(0).stringAt(0));
            }

            final Frame testPreds = se.score(test);
            Scope.track(testPreds);

            assertEquals("V", testPreds.vec(0).stringAt(0));
        } catch (IllegalArgumentException e) {

        } finally {
            Scope.exit();
            for (Lockable l: deletables) {
                if (l instanceof Model) ((Model)l).deleteCrossValidationPreds();
                l.delete();
            }
        }
    }

    @Test public void testSE_CategoricalEncodingWithPredictionsOnFeaturesSubset() {
        if (encoding == Model.Parameters.CategoricalEncodingScheme.OneHotInternal) return; //not supported for Tree algos
        Log.info("Using encoding "+encoding);

        List<Lockable> deletables = new ArrayList<>();
        try {
            Scope.enter();
            final Frame train = new TestFrameBuilder()
                    .withName("trainEncoding")
                    .withColNames("ColA", "ColB", "Response")
                    .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                    .withDataForCol(0, ar("B", "B", "A", "A", "A", "B", "A"))
                    .withDataForCol(1, ar(2, 2, 1, 1, 1, 2, 1))
                    .withDataForCol(2, ar("C", "C", "V", "V", "V", "C", "V"))
                    .withDomain(0, ar("B", "A"))  //XXX: red flag, this test fails for OneHotExplicit and Binary encoding if the domain is sorted in lexicographical order (which is H2O-3 default...)
                    .build();
            String target = "Response";

            DRFModel.DRFParameters params = new DRFModel.DRFParameters();
            params._train = train._key;
            params._response_column = target;
            params._min_rows = 1;
            params._sample_rate = 1;
            params._col_sample_rate_per_tree = .5;
            params._seed = 1;
            params._nfolds = 2;
            params._keep_cross_validation_models = false;
            params._keep_cross_validation_predictions = true;
            params._categorical_encoding = encoding;
            if (encoding == Model.Parameters.CategoricalEncodingScheme.EnumLimited) {
                params._max_categorical_levels = 2;
            }

            Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, new HashMap<String, Object[]>() {{
                put("_ntrees", new Integer[]{1, 2});
                put("_max_depth", new Integer[]{2, 3});
            }});
            Grid grid = gridSearch.get();
            deletables.add(grid);
            Model[] gridModels = grid.getModels();
            deletables.addAll(Arrays.asList(gridModels));
            assertEquals(4, gridModels.length);

            StackedEnsembleParameters seParams = new StackedEnsembleParameters();
            seParams._train = train._key;
            seParams._response_column = target;
            seParams._base_models = ArrayUtils.append(grid.getModelKeys());
            seParams._seed = 1;
            StackedEnsembleModel se = new StackedEnsemble(seParams).trainModel().get();
            deletables.add(se);

            Frame trainPreds = se.score(train);
            Scope.track(trainPreds);

            final Frame test_cat = new TestFrameBuilder()
                    .withName("testEncodingCat")
                    .withColNames("ColA", "ColZ")
                    .withVecTypes(Vec.T_CAT, Vec.T_NUM)
                    .withDataForCol(0, ar("A"))
                    .withDataForCol(1, ard(1/3))
                    .build();

            for (Model model : gridModels) {
                final Frame testPreds = model.score(test_cat);
                Scope.track(testPreds);
                assertEquals("V", testPreds.vec(0).stringAt(0));
            }
            final Frame testPreds = se.score(test_cat);
            Scope.track(testPreds);
            assertEquals("V", testPreds.vec(0).stringAt(0));

            final Frame test_num = new TestFrameBuilder()
                    .withName("testEncodingNum")
                    .withColNames("ColB")
                    .withVecTypes(Vec.T_NUM)
                    .withDataForCol(0, ar(1))
                    .build();

            for (Model model : gridModels) {
                final Frame testPreds2 = model.score(test_num);
                Scope.track(testPreds2);
                assertEquals("V", testPreds2.vec(0).stringAt(0));
            }
            final Frame testPreds2 = se.score(test_num);
            Scope.track(testPreds2);
            assertEquals("V", testPreds2.vec(0).stringAt(0));

            final Frame test_no_common = new TestFrameBuilder()
                    .withName("testEncodingNoCommon")
                    .withColNames("ColZ")
                    .withVecTypes(Vec.T_NUM)
                    .withDataForCol(0, ar(1))
                    .build();
            for (Model model : gridModels) {
                try {
                    Scope.track(model.score(test_no_common));
                    fail("Should have thrown IllegalArgumentException");
                } catch (IllegalArgumentException e) {
                    assertTrue("Expected exception due to no column in common with training data, but got: "+e.getMessage(),
                            e.getMessage().contains("no columns in common"));
                }
            }

            try {
                Scope.track(se.score(test_no_common));
                fail("Should have thrown IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                assertTrue("Expected exception due to no column in common with training data, but got: "+e.getMessage(),
                        e.getMessage().contains("no columns in common"));
            }

        } finally {
            Scope.exit();
            for (Lockable l: deletables) {
                if (l instanceof Model) ((Model)l).deleteCrossValidationPreds();
                l.delete();
            }
        }
    }
}
