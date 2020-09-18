package ai.h2o.automl.preprocessing;

import ai.h2o.automl.*;
import ai.h2o.automl.dummy.DummyModel;
import ai.h2o.automl.preprocessing.PreprocessingStepDefinition.Type;
import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderPreprocessor;
import hex.Model;
import hex.SplitFrame;
import hex.ensemble.StackedEnsembleModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Keyed;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static water.TestUtil.*;
import static water.TestUtil.ar;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class TargetEncodingTest {
    
    private List<Keyed> toDelete = new ArrayList<>();
    private AutoML aml;
    private Frame fr;

    @Before
    public void setup() {
        fr = new TestFrameBuilder()
                .withName("dummy_fr")
                .withColNames("cat1", "numerical", "cat2", "target", "foldc")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "c", "a", "b", "c"))
                .withDataForCol(1, ard(1, 2, 5, 1.5, 3, 4))
                .withDataForCol(2, ar("s", null, "t", "t", null, "s"))
                .withDataForCol(3, ar("yes", "no", "no", "yes", "yes", "no"))
                .withDataForCol(4, ar(1, 1, 1, 2, 2, 2))
                .build();
        DKV.put(fr); toDelete.add(fr);
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        buildSpec.input_spec.training_frame = fr._key;
        buildSpec.input_spec.response_column = "target";
        aml = new AutoML(null, new Date(), buildSpec);
        DKV.put(aml); toDelete.add(aml);
    }

    @After
    public void cleanup() {
        toDelete.forEach(Keyed::remove);
    }


    @Test
    public void test_te_preprocessing_lifecycle_automl_no_cv() {
        aml.getBuildSpec().build_control.nfolds = 0; //disabling CV on AutoML
        TargetEncoding te = new TargetEncoding(aml);
        assertNull(te.getTEModel());
        assertNull(te.getTEPreprocessor());
        try {
            Scope.enter();
            te.prepare();
            assertNotNull(te.getTEModel());
            assertNotNull(te.getTEPreprocessor());
            Scope.track_generic(te.getTEModel());
            Scope.track_generic(te.getTEPreprocessor());
            assertNull(te.getTEModel()._parms._fold_column);
            assertEquals(DataLeakageHandlingStrategy.None, te.getTEModel()._parms._data_leakage_handling);
            assertFalse(te.getTEModel()._parms._keep_original_categorical_columns);

            Model.Parameters params = new DummyModel.DummyModelParameters();
            params._train = fr._key;
            params._nfolds = 0;
            params._fold_column = null;

            PreprocessingStep.Completer complete = te.apply(params);
            assertEquals(0, params._nfolds);
            assertNull(params._fold_column);
            complete.run();
        } finally {
            te.dispose();
            Scope.exit();
        }
    }


    @Test
    public void test_te_preprocessing_lifecycle_with_automl_cv_nfolds() {
        int nfolds = 3;
        aml.getBuildSpec().build_control.nfolds = nfolds;
        TargetEncoding te = new TargetEncoding(aml);
        try {
            Scope.enter();
            te.prepare();
            assertNotNull(te.getTEModel());
            assertNotNull(te.getTEPreprocessor());
            Scope.track_generic(te.getTEModel());
            Scope.track_generic(te.getTEPreprocessor());
            assertNotNull(te.getTEModel()._parms._fold_column);
            assertTrue(te.getTEModel()._parms._fold_column.endsWith(TargetEncoding.TE_FOLD_COLUMN_SUFFIX));
            assertEquals(DataLeakageHandlingStrategy.KFold, te.getTEModel()._parms._data_leakage_handling);

            Model.Parameters params = new DummyModel.DummyModelParameters();
            params._train = fr._key;
            params._nfolds = nfolds;
            params._fold_column = null;

            PreprocessingStep.Completer complete = te.apply(params);
            assertEquals(0, params._nfolds);
            assertNotNull(params._fold_column);
            assertEquals(te.getTEModel()._parms._fold_column, params._fold_column);
            assertNotEquals(fr._key, params._train);
            Frame newTrain = params._train.get();
            assertTrue(ArrayUtils.contains(newTrain.names(), params._fold_column));
            assertFalse(ArrayUtils.contains(fr.names(), params._fold_column));
            assertEquals(nfolds, newTrain.vec(params._fold_column).toCategoricalVec().cardinality());
            complete.run();
        } finally {
            te.dispose();
            Scope.exit();
        }
    }
    
    @Test
    public void test_te_preprocessing_lifecycle_with_automl_cv_foldcolumn() {
        aml.getBuildSpec().input_spec.fold_column = "foldc";
        TargetEncoding te = new TargetEncoding(aml);
        try {
            Scope.enter();
            te.prepare();
            assertNotNull(te.getTEModel());
            assertNotNull(te.getTEPreprocessor());
            Scope.track_generic(te.getTEModel());
            Scope.track_generic(te.getTEPreprocessor());
            assertNotNull(te.getTEModel()._parms._fold_column);
            assertEquals("foldc", te.getTEModel()._parms._fold_column);
            assertEquals(DataLeakageHandlingStrategy.KFold, te.getTEModel()._parms._data_leakage_handling);

            Model.Parameters params = new DummyModel.DummyModelParameters();
            params._train = fr._key;
            params._nfolds = 0;
            params._fold_column = "foldc";

            PreprocessingStep.Completer complete = te.apply(params);
            assertEquals(0, params._nfolds);
            assertNotNull(params._fold_column);
            assertEquals("foldc", params._fold_column);
            assertEquals(te.getTEModel()._parms._fold_column, params._fold_column);
            assertEquals(fr._key, params._train);
            complete.run();
        } finally {
            te.dispose();
            Scope.exit();
        }
    }
    
    
    @Test
    public void test_automl_run_with_target_encoding_enabled() {
        try {
            Scope.enter();
            AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
            Frame fr = parse_test_file("./smalldata/titanic/titanic_expanded.csv"); Scope.track(fr);
            SplitFrame sf = new SplitFrame(fr, new double[] { 0.7, 0.3 }, new Key[]{Key.make("titanic_train"), Key.make("titanic_test")});
            sf.exec().get();
            Frame train = sf._destination_frames[0].get(); Scope.track(train);
            Frame test = sf._destination_frames[1].get(); Scope.track(test);
            
            autoMLBuildSpec.input_spec.training_frame = train._key;
            autoMLBuildSpec.input_spec.leaderboard_frame = test._key;
            autoMLBuildSpec.input_spec.response_column = "survived";
            autoMLBuildSpec.build_control.stopping_criteria.set_max_models(15); // sth big enough to test all algos+grids with TE
            autoMLBuildSpec.build_control.nfolds = 3;
            autoMLBuildSpec.build_models.exclude_algos = new Algo[] {Algo.GLM}; // one key leaking with GLM, investigating
            autoMLBuildSpec.build_models.preprocessing = new PreprocessingStepDefinition[] {
                    new PreprocessingStepDefinition(Type.TargetEncoding)
            };

            aml = AutoML.startAutoML(autoMLBuildSpec); Scope.track_generic(aml);
            aml.get();
            System.out.println(aml.leaderboard().toTwoDimTable());
            for (Model m : aml.leaderboard().getModels()) {
                if (m instanceof StackedEnsembleModel) {
                    assertNull(m._parms._preprocessors);
                } else {
                    assertNotNull(m._parms._preprocessors);
                    assertEquals(1, m._parms._preprocessors.length);
                    assertTrue(m._parms._preprocessors[0].get() instanceof TargetEncoderPreprocessor);
                }
            }
        } finally {
            Scope.exit();
        }
    }
}
