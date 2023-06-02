package ai.h2o.automl;

import ai.h2o.automl.dummy.DummyBuilder;
import ai.h2o.automl.dummy.DummyModel;
import ai.h2o.automl.dummy.DummyStepsProvider;
import ai.h2o.automl.dummy.DummyStepsProvider.DummyModelStep;
import hex.Model;
import hex.ScoreKeeper;
import hex.grid.Grid;
import hex.grid.HyperSpaceSearchCriteria;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Job;
import water.Key;
import water.Keyed;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static water.TestUtil.cvec;
import static water.TestUtil.ivec;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ModelingStepTest {

    private List<Keyed> toDelete = new ArrayList<>();
    private AutoML aml;
    private Frame fr;

    @Before
    public void setup() {
        DummyStepsProvider provider = new DummyStepsProvider();
        provider.modelStepsFactory = TestingModelSteps::new;
        ModelingStepsRegistry.registerProvider(provider);

        fr = new Frame(
                Key.make("dummy_fr"),
                new String[]{"A", "B", "target"},
                new Vec[]{
                        ivec(1, 2, 3, 4, 5),
                        ivec(1, 2, 3, 4, 5),
                        cvec("foo", "foo", "foo", "bar", "bar"),
                }
        );
        DKV.put(fr); toDelete.add(fr);
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        buildSpec.input_spec.training_frame = fr._key;
        buildSpec.input_spec.response_column = "target";
        buildSpec.build_models.modeling_plan = new StepDefinition[] {
                new StepDefinition("dummy")
        };
        buildSpec.build_models.exploitation_ratio = 0.5;
        aml = new AutoML(buildSpec);
        aml.planWork();
        DKV.put(aml); toDelete.add(aml);
    }

    @After
    public void cleanup() {
        toDelete.forEach(Keyed::remove);
    }

    @Test public void test_ModelStep() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_model".equals(s._id)).findFirst().get();
        Job<DummyModel> job = step.run();
        DummyModel model = job.get(); toDelete.add(model);
        assertNotNull(model);
        assertEquals(aml.getBuildSpec().input_spec.response_column, model._parms._response_column);
        assertEquals(aml.getBuildSpec().build_control.nfolds, model._parms._nfolds);
        assertTrue(model._parms._max_runtime_secs > 0);
        assertEquals(ScoreKeeper.StoppingMetric.logloss, model._parms._stopping_metric); // Classification
    }

    @Test(expected = H2OIllegalArgumentException.class)
    public void test_failing_ModelStep_propagates_error() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_model_failing".equals(s._id)).findFirst().get();
        step.run();
    }

    @Test public void test_GridStep() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_grid".equals(s._id)).findFirst().get();
        Job<Grid> job = step.run();
        Grid grid = job.get(); toDelete.add(grid);
        assertEquals(3, grid.getModelCount());
        for (Model model : grid.getModels()) {
            assertEquals(aml.getBuildSpec().input_spec.response_column, model._parms._response_column);
            assertEquals(aml.getBuildSpec().build_control.nfolds, model._parms._nfolds);
            assertTrue(model._parms._max_runtime_secs > 0);
            assertEquals(ScoreKeeper.StoppingMetric.logloss, model._parms._stopping_metric); // Classification
        }
    }

    @Test public void test_SelectionStep_with_single_model() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_exploitation_single".equals(s._id)).findFirst().get();
        Job<Models> job = step.run();
        Models models = job.get(); toDelete.add(models);
        assertEquals(1, models.getModelCount());
        for (Model model : models.getModels()) {
            assertEquals(aml.getBuildSpec().input_spec.response_column, model._parms._response_column);
            assertEquals(aml.getBuildSpec().build_control.nfolds, model._parms._nfolds);
            assertTrue(model._parms._max_runtime_secs > 0);
            assertEquals(aml.getBuildSpec().build_control.stopping_criteria.stopping_metric(), model._parms._stopping_metric);
        }
    }

    @Test public void test_SelectionStep_with_multiple_models() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_exploitation_multi".equals(s._id)).findFirst().get();
        Job<Models> job = step.run();
        Models models = job.get(); toDelete.add(models);
        assertEquals(4, models.getModelCount());
        for (Model model : models.getModels()) {
            assertEquals(aml.getBuildSpec().input_spec.response_column, model._parms._response_column);
            assertEquals(aml.getBuildSpec().build_control.nfolds, model._parms._nfolds);
            assertTrue(model._parms._max_runtime_secs > 0);
            assertEquals(aml.getBuildSpec().build_control.stopping_criteria.stopping_metric(), model._parms._stopping_metric);
        }
    }
    
    public void test_DynamicStep_with_no_substeps_and_no_main_step() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_dynamic_nothing".equals(s._id)).findFirst().get();
        assertFalse(step.canRun());
        assertNull(step.run());
        assertFalse(step.iterateSubSteps().hasNext());
    }
    
    @Test public void test_DynamicStep_with_substeps_but_no_main_step() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_dynamic_no_main".equals(s._id)).findFirst().get();
        assertFalse(step.canRun());
        assertNull(step.run());
        assertTrue(step.iterateSubSteps().hasNext());
        List<Model> models = new ArrayList<>();
        for (Iterator<ModelingStep> it = step.iterateSubSteps(); it.hasNext(); ) {
            Job<Model> job = it.next().run();
            models.add(job.get()); 
        }
        toDelete.addAll(models);
        assertEquals(3, models.size());
        for (Model model : models) {
            assertEquals(aml.getBuildSpec().input_spec.response_column, model._parms._response_column);
            assertEquals(aml.getBuildSpec().build_control.nfolds, model._parms._nfolds);
            assertTrue(model._parms._max_runtime_secs > 0);
            assertEquals(ScoreKeeper.StoppingMetric.logloss, model._parms._stopping_metric); // Classification
        }
    }
    
    @Test public void test_DynamicStep_with_substeps_and_main_step() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_dynamic_substeps_and_main".equals(s._id)).findFirst().get();
        assertTrue(step.canRun());
        assertTrue(step.iterateSubSteps().hasNext());
        List<Model> models = new ArrayList<>();
        for (Iterator<ModelingStep> it = step.iterateSubSteps(); it.hasNext(); ) {
            Job<Model> job = it.next().run();
            models.add(job.get());
        }
        models.add(((Job<Model>)step.run()).get());
        toDelete.addAll(models);
        assertEquals(4, models.size());
        for (Model model : models) {
            assertEquals(aml.getBuildSpec().input_spec.response_column, model._parms._response_column);
            assertEquals(aml.getBuildSpec().build_control.nfolds, model._parms._nfolds);
            assertTrue(model._parms._max_runtime_secs > 0);
            assertEquals(ScoreKeeper.StoppingMetric.logloss, model._parms._stopping_metric); // Classification
        }
    }


    private class TestingModelSteps extends DummyStepsProvider.DummyModelSteps {

        public TestingModelSteps(AutoML autoML) {
            super(autoML);
            defaultModels = new ModelingStep[] {
                    new DummyModelStep(DummyBuilder.algo, "dummy_model", aml()),
                    new FailingDummyModelStep(DummyBuilder.algo, "dummy_model_failing", aml())
            };

            grids = new ModelingStep[] {
                    new DummyGridStep(DummyBuilder.algo, "dummy_grid", aml())
            };

            optionals = new ModelingStep[] {
                    new DummySelectionStep(DummyBuilder.algo, "dummy_exploitation_single", false, aml()),
                    new DummySelectionStep(DummyBuilder.algo, "dummy_exploitation_multi", true, aml()),
                    new DummyDynamicStep("dummy_dynamic_nothing", 0, false, aml()),
                    new DummyDynamicStep("dummy_dynamic_no_main", 3, false, aml()),
                    new DummyDynamicStep("dummy_dynamic_substeps_and_main", 3, true, aml()),
            };

        }
    }

    private static class FailingDummyModelStep extends DummyModelStep {
        public FailingDummyModelStep(IAlgo algo, String id, AutoML autoML) {
            super(algo, id, autoML);
        }

        @Override
        public Model.Parameters prepareModelParameters() {
            DummyModel.DummyModelParameters params = new DummyModel.DummyModelParameters();
            params._fail_on_init = true;
            return params;
        }
    }

    private static class DummyGridStep extends ModelingStep.GridStep<DummyModel> {

        public DummyGridStep(IAlgo algo, String id, AutoML autoML) {
            super(TestingModelSteps.NAME, algo, id, autoML);
        }

        @Override
        public Model.Parameters prepareModelParameters() {
            return new DummyModel.DummyModelParameters();
        }

        @Override
        public Map<String, Object[]> prepareSearchParameters() {
            Map<String, Object[]> searchParams = new HashMap<>();
            searchParams.put("_tag", new String[] {"one", "two", "three"});
            return searchParams;
        }
    }

    private static class DummySelectionStep extends ModelingStep.SelectionStep<DummyModel> {
        boolean _useSearch;

        public DummySelectionStep(IAlgo algo, String id, boolean useSearch, AutoML autoML) {
            super(TestingModelSteps.NAME, algo, id, autoML);
            _useSearch = useSearch;
        }

        @Override
        protected Job<Models> startTraining(Key result, double maxRuntimeSecs) {
            Model.Parameters params = new DummyModel.DummyModelParameters();
            setCommonModelBuilderParams(params);
            params._max_runtime_secs = maxRuntimeSecs;
            Job job;
            if (_useSearch) {
                Map<String, Object[]> searchParams = new HashMap<>();
                searchParams.put("_tag", new String[] {"uno", "due", "tre", "quattro"});
                job = startSearch(Key.make(result+"_expsearch"), params, searchParams, new HyperSpaceSearchCriteria.CartesianSearchCriteria());
            } else {
                job = startModel(Key.make(result+"_expmodel"), params);
            }
            return asModelsJob(job, result);
        }

        @Override
        protected ModelSelectionStrategy getSelectionStrategy() {
            return new ModelSelectionStrategies.KeepBestN(10, () -> makeTmpLeaderboard("for_selection"));
        }
    }
    
    private static class DummyDynamicStep extends ModelingStep.DynamicStep<DummyModel> {

        private int _numSubSteps;
        private boolean _mainStepEnabled;
        
        public DummyDynamicStep(String id, int numSubSteps, boolean mainStepEnabled, AutoML autoML) {
            super(TestingModelSteps.NAME, id, autoML);
            _numSubSteps = numSubSteps;
            _mainStepEnabled = mainStepEnabled;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Collection<ModelingStep> prepareModelingSteps() {
            return IntStream.rangeClosed(1, _numSubSteps)
                    .mapToObj(i -> new DummyModelStep(DummyBuilder.algo, "dummy_model_"+i, true, aml()))
                    .collect(Collectors.toList());
        }

        @Override
        public boolean canRun() {
            if (!_mainStepEnabled) return super.canRun();
            return true;
        }

        @Override
        protected Job startJob() {
            if (!_mainStepEnabled) return super.startJob();
            return new DummyModelStep(DummyBuilder.algo, "dummy_model_main", true, aml()).run();
        }
    }
}
