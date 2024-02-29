package ai.h2o.automl.preprocessing;

import ai.h2o.automl.*;
import ai.h2o.automl.dummy.DummyBuilder;
import ai.h2o.automl.dummy.DummyStepsProvider;
import ai.h2o.automl.dummy.DummyStepsProvider.DummyGridStep;
import ai.h2o.automl.dummy.DummyStepsProvider.DummyModelStep;
import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import ai.h2o.targetencoding.pipeline.transformers.TargetEncoderFeatureTransformer;
import hex.Model;
import hex.SplitFrame;
import hex.deeplearning.DeepLearningModel;
import hex.ensemble.StackedEnsembleModel;
import hex.pipeline.DataTransformer;
import hex.pipeline.PipelineModel;
import org.junit.*;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.junit.rules.ScopeTracker;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import static org.junit.Assert.*;
import static water.TestUtil.*;
import static water.TestUtil.ar;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class TargetEncodingTest {
  
    @FunctionalInterface
    interface Callback<T> {
      void call(T t);
    }

    @Rule
    public ScopeTracker scope = new ScopeTracker();
    
    @BeforeClass 
    public static void setupDummySteps() {
      DummyStepsProvider provider = new DummyStepsProvider();
      provider.modelStepsFactory = DummySteps::new;
      ModelingStepsRegistry.registerProvider(provider);
    }
    
    private AutoML runDummyAutoML(Callback<AutoMLBuildSpec> configureBuildSpec) {
        Frame fr = new TestFrameBuilder()
                .withName("dummy_fr")
                .withColNames("cat1", "numerical", "cat2", "target", "foldc")
                .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
                .withDataForCol(0, ar("a", "b", "c", "a", "b", "c"))
                .withDataForCol(1, ard(1, 2, 5, 1.5, 3, 4))
                .withDataForCol(2, ar("s", null, "t", "t", null, "s"))
                .withDataForCol(3, ar("yes", "no", "no", "yes", "yes", "no"))
                .withDataForCol(4, ar(1, 1, 1, 2, 2, 2))
                .build();
        DKV.put(fr);
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        buildSpec.input_spec.training_frame = fr._key;
        buildSpec.input_spec.response_column = "target";
        buildSpec.build_models.preprocessing = new PipelineStepDefinition[] {
                new TEStepDefinition()
        };
        buildSpec.build_models.modeling_plan = new StepDefinition[] {
                new StepDefinition("dummy", StepDefinition.Alias.defaults)
        };
        configureBuildSpec.call(buildSpec);
        AutoML aml = Scope.track_generic(AutoML.startAutoML(buildSpec));
        aml.get();
        return aml;
    }

    @Test
    public void test_default_params() {
        AutoML aml = runDummyAutoML(spec -> {
          spec.build_control.nfolds = 0; //disabling CV on AutoML
        });
        
        Model m = aml.leaderboard().getLeader();
        DataTransformer[] transformers = ((PipelineModel) m)._output.getTransformers();
        assertNotNull(transformers);
        assertEquals(1, transformers.length);
        TargetEncoderFeatureTransformer teTrans = (TargetEncoderFeatureTransformer)transformers[0];
        TargetEncoderParameters teParams = teTrans.getModel()._parms;
        assertNull(teParams._fold_column);
        assertEquals(DataLeakageHandlingStrategy.None, teParams._data_leakage_handling);
        assertFalse(teParams._keep_original_categorical_columns);
        assertTrue(teParams._blending);
        assertEquals(0, teParams._noise, 0);
    }

    @Test
    public void test_te_pipeline_lifecycle_automl_no_cv() {
        AutoML aml = runDummyAutoML(spec -> {
          spec.build_control.nfolds = 0; //disabling CV on AutoML
        });
        PipelineModel m = (PipelineModel) aml.leaderboard().getLeader();
        DataTransformer[] transformers = m._output.getTransformers();
        assertNotNull(transformers);
        assertEquals(1, transformers.length);
        TargetEncoderFeatureTransformer teTrans = (TargetEncoderFeatureTransformer)transformers[0];
        TargetEncoderParameters teParams = teTrans.getModel()._parms;
        assertNull(teParams._fold_column);
        assertEquals(DataLeakageHandlingStrategy.None, teParams._data_leakage_handling);

        Model.Parameters eParams = m._output.getEstimatorModel()._parms;
        assertEquals(0, eParams._nfolds);
        assertNull(eParams._fold_column);
    }


    @Test
    public void test_te_pipeline_lifecycle_with_automl_cv_nfolds() {
        int nfolds = 3;
        AutoML aml = runDummyAutoML(spec -> {
          spec.build_control.nfolds = nfolds;
          spec.build_control.keep_cross_validation_models = true;
        });
        PipelineModel m = (PipelineModel) aml.leaderboard().getLeader();
        DataTransformer[] transformers = m._output.getTransformers();
        assertNotNull(transformers);
        assertEquals(2, transformers.length); //with CV enabled and no fold column, an additional transformer is added to generate the latter, 
        TargetEncoderFeatureTransformer teTrans = (TargetEncoderFeatureTransformer)transformers[1];
        TargetEncoderParameters teParams = teTrans.getModel()._parms;
        assertNotNull(teParams._fold_column);
        assertEquals("__fold__target", teParams._fold_column);
        assertTrue(teParams._fold_column.endsWith("target"));
        assertEquals(DataLeakageHandlingStrategy.KFold, teParams._data_leakage_handling);

        Model eModel = m._output.getEstimatorModel();
        assertEquals(0, eModel._parms._nfolds);
        assertNotNull(eModel._parms._fold_column);

        assertEquals(teParams._fold_column, eModel._parms._fold_column);
        assertNotEquals(aml.getBuildSpec().input_spec.training_frame, eModel._parms._train);
        Frame amlTrain = aml.getTrainingFrame();
        assertTrue(ArrayUtils.contains(eModel._output._names, eModel._parms._fold_column));
        assertFalse(ArrayUtils.contains(amlTrain.names(), eModel._parms._fold_column));
        assertEquals(nfolds, m._output._cross_validation_models.length);
        assertArrayEquals(m._output._cross_validation_models, m._output.getEstimatorModel()._output._cross_validation_models); //temporary until estimator CV models can be translated into pipeline models.
    }

    @Test
    public void test_te_pipeline_lifecycle_with_automl_cv_foldcolumn() {
        String foldc = "foldc";
        AutoML aml = runDummyAutoML(spec -> {
          spec.input_spec.fold_column = foldc;
          spec.build_control.keep_cross_validation_models = true;
        });
        PipelineModel m = (PipelineModel) aml.leaderboard().getLeader();
        DataTransformer[] transformers = m._output.getTransformers();
        assertNotNull(transformers);
        assertEquals(2, transformers.length); //with CV enabled and no fold column, an additional transformer is added to generate the latter, 
        TargetEncoderFeatureTransformer teTrans = (TargetEncoderFeatureTransformer)transformers[1];
        TargetEncoderParameters teParams = teTrans.getModel()._parms;
        assertNotNull(teParams._fold_column);
        assertEquals(foldc, teParams._fold_column);
        assertEquals(DataLeakageHandlingStrategy.KFold, teParams._data_leakage_handling);

        Model eModel = m._output.getEstimatorModel();
        assertEquals(0, eModel._parms._nfolds);
        assertNotNull(eModel._parms._fold_column);

        assertEquals(foldc, eModel._parms._fold_column);
        assertNotEquals(aml.getBuildSpec().input_spec.training_frame, eModel._parms._train);
        assertEquals(2, m._output._cross_validation_models.length); // foldc has 2 distinct values
        assertArrayEquals(m._output._cross_validation_models, m._output.getEstimatorModel()._output._cross_validation_models); //temporary until estimator CV models can be translated into pipeline models.
    }


    @Test
    public void test_automl_run_with_target_encoding_enabled() {
        AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
        Frame fr = parseTestFile("./smalldata/titanic/titanic_expanded.csv"); Scope.track(fr);
        SplitFrame sf = new SplitFrame(fr, new double[] { 0.7, 0.3 }, new Key[]{Key.make("titanic_train"), Key.make("titanic_test")});
        sf.exec().get();
        Frame train = sf._destination_frames[0].get(); Scope.track(train);
        Frame test = sf._destination_frames[1].get(); Scope.track(test);

        autoMLBuildSpec.input_spec.training_frame = train._key;
        autoMLBuildSpec.input_spec.validation_frame = test._key;
        autoMLBuildSpec.input_spec.leaderboard_frame = test._key;
        autoMLBuildSpec.input_spec.response_column = "survived";
        autoMLBuildSpec.build_control.stopping_criteria.set_max_models(15); // sth big enough to test all algos+grids with TE
        autoMLBuildSpec.build_control.stopping_criteria.set_seed(42);
        autoMLBuildSpec.build_control.nfolds = 3;
        autoMLBuildSpec.build_models.preprocessing = new PipelineStepDefinition[] {
                new PipelineStepDefinition(PipelineStepDefinition.Type.TargetEncoding)
        };
//        autoMLBuildSpec.build_models.exclude_algos = aro(Algo.DeepLearning);

        AutoML aml = AutoML.startAutoML(autoMLBuildSpec); Scope.track_generic(aml);
        aml.get();
        System.out.println(aml.leaderboard().toTwoDimTable());
        for (Model m : aml.leaderboard().getModels()) {
            if (m instanceof StackedEnsembleModel) {
                assertFalse(m.haveMojo()); // all SEs should not support MOJO as their base models don't
                assertFalse(m.havePojo());
            } else {
                assertTrue(m instanceof PipelineModel);
                PipelineModel p = (PipelineModel)m;
                if (p._output.getEstimatorModel() instanceof DeepLearningModel) {
                  assertEquals(1, p._output.getTransformers().length); // TE disabled for DL, but keeping the fold column generator for CV consistency with other models when building SE.
                } else {
                  assertEquals(2, p._input_parms._transformers.length);
                  if (p._input_parms._transformers[1].get() != null) {
                    assertEquals(2, p._output.getTransformers().length);
                    assertTrue(p._output.getTransformers()[1].enabled());
                  } else {
                    assertEquals(1, p._output.getTransformers().length);
                    assertTrue(p._key.toString().contains("_grid_"));  // TE can be disabled during grid search as an hyperparam.
                  }
                }
                assertFalse(m.haveMojo());
                assertFalse(m.havePojo());
            }
        }
    }
    
    private static class DummySteps extends DummyStepsProvider.DummyModelSteps {

      public DummySteps(AutoML autoML) {
        super(autoML);
        defaultModels = new ModelingStep[] {
                new DummyModelStep(DummyBuilder.algo, "dummy_model", aml()),
        };

        grids = new ModelingStep[] {
                new DummyGridStep(DummyBuilder.algo, "dummy_grid", aml())
        };
      }
    }
    private static class TEStepDefinition extends PipelineStepDefinition {

      public TEStepDefinition() {
        super(Type.TargetEncoding);
      }

      @Override
        public PipelineStep newPipelineStep(AutoML aml) {
          TargetEncoding teStep = (TargetEncoding) super.newPipelineStep(aml);
          teStep.setEncodeAllColumns(true); //enforce as we use small data in those tests
          return teStep;
        }
    }

}
