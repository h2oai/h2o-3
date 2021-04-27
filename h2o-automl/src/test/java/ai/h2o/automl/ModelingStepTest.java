package ai.h2o.automl;

import ai.h2o.automl.dummy.DummyBuilder;
import ai.h2o.automl.dummy.DummyModel;
import ai.h2o.automl.dummy.DummyStepsProvider;
import hex.Model;
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
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.*;

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
        aml = new AutoML(null, new Date(), buildSpec);
        DKV.put(aml); toDelete.add(aml);
    }

    @After
    public void cleanup() {
        toDelete.forEach(Keyed::remove);
    }

    @Test public void testModelStep() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_model".equals(s._id)).findFirst().get();
        Job<DummyModel> job = step.startJob();
        DummyModel model = job.get(); toDelete.add(model);
        assertNotNull(model);
        assertEquals(aml.getBuildSpec().input_spec.response_column, model._parms._response_column);
        assertEquals(aml.getBuildSpec().build_control.nfolds, model._parms._nfolds);
        assertTrue(model._parms._max_runtime_secs > 0);
        assertEquals(aml.getBuildSpec().build_control.stopping_criteria.stopping_metric(), model._parms._stopping_metric);
    }

    @Test public void testFailingModelStep() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_model_failing".equals(s._id)).findFirst().get();
        Job<DummyModel> job = step.startJob();
        assertNull(job);
    }

    @Test public void testGridStep() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_grid".equals(s._id)).findFirst().get();
        Job<Grid> job = step.startJob();
        Grid grid = job.get(); toDelete.add(grid);
        assertEquals(3, grid.getModelCount());
        for (Model model : grid.getModels()) {
            assertEquals(aml.getBuildSpec().input_spec.response_column, model._parms._response_column);
            assertEquals(aml.getBuildSpec().build_control.nfolds, model._parms._nfolds);
            assertTrue(model._parms._max_runtime_secs > 0);
            assertEquals(aml.getBuildSpec().build_control.stopping_criteria.stopping_metric(), model._parms._stopping_metric);
        }
    }

    @Test public void testSelectionStepSingleModel() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_exploitation_single".equals(s._id)).findFirst().get();
        Job<Models> job = step.startJob();
        Models models = job.get(); toDelete.add(models);
        assertEquals(1, models.getModelCount());
        for (Model model : models.getModels()) {
            assertEquals(aml.getBuildSpec().input_spec.response_column, model._parms._response_column);
            assertEquals(aml.getBuildSpec().build_control.nfolds, model._parms._nfolds);
            assertTrue(model._parms._max_runtime_secs > 0);
            assertEquals(aml.getBuildSpec().build_control.stopping_criteria.stopping_metric(), model._parms._stopping_metric);
        }
    }

    @Test public void testSelectionStepMultipleModels() {
        ModelingStep step = Arrays.stream(aml.getExecutionPlan()).filter(s -> "dummy_exploitation_multi".equals(s._id)).findFirst().get();
        Job<Models> job = step.startJob();
        Models models = job.get(); toDelete.add(models);
        assertEquals(4, models.getModelCount());
        for (Model model : models.getModels()) {
            assertEquals(aml.getBuildSpec().input_spec.response_column, model._parms._response_column);
            assertEquals(aml.getBuildSpec().build_control.nfolds, model._parms._nfolds);
            assertTrue(model._parms._max_runtime_secs > 0);
            assertEquals(aml.getBuildSpec().build_control.stopping_criteria.stopping_metric(), model._parms._stopping_metric);
        }
    }


    private class TestingModelSteps extends DummyStepsProvider.DummyModelSteps {

        public TestingModelSteps(AutoML autoML) {
            super(autoML);
            defaultModels = new ModelingStep[] {
                    new DummyModelStep(DummyBuilder.algo, "dummy_model", 10, aml()),
                    new FailingDummyModelStep(DummyBuilder.algo, "dummy_model_failing", 0, aml())
            };

            grids = new ModelingStep[] {
                    new DummyGridStep(DummyBuilder.algo, "dummy_grid", 50, aml())
            };

            exploitation = new ModelingStep[] {
                    new DummySelectionStep(DummyBuilder.algo, "dummy_exploitation_single", 10, aml(), false),
                    new DummySelectionStep(DummyBuilder.algo, "dummy_exploitation_multi", 10, aml(), true)
            };

        }
    }

    private static class DummyModelStep extends ModelingStep.ModelStep<DummyModel> {
        public DummyModelStep(IAlgo algo, String id, int cost, AutoML autoML) {
            super(algo, id, cost, autoML);
        }

        @Override
        protected Job<DummyModel> startJob() {
            Model.Parameters params = new DummyModel.DummyModelParameters();
            return trainModel(params);
        }
    }

    private static class FailingDummyModelStep extends ModelingStep.ModelStep<DummyModel> {
        public FailingDummyModelStep(IAlgo algo, String id, int cost, AutoML autoML) {
            super(algo, id, cost, autoML);
        }

        @Override
        protected Job<DummyModel> startJob() {
            DummyModel.DummyModelParameters params = new DummyModel.DummyModelParameters();
            params._fail_on_init = true;
            return trainModel(params);
        }
    }

    private static class DummyGridStep extends ModelingStep.GridStep<DummyModel> {

        public DummyGridStep(IAlgo algo, String id, int cost, AutoML autoML) {
            super(algo, id, cost, autoML);
        }

        @Override
        protected Job<Grid> startJob() {
            Model.Parameters params = new DummyModel.DummyModelParameters();
            Map<String, Object[]> searchParams = new HashMap<>();
            searchParams.put("_tag", new String[] {"one", "two", "three"});
            return hyperparameterSearch(params, searchParams);
        }
    }

    private static class DummySelectionStep extends ModelingStep.SelectionStep<DummyModel> {
        boolean _useSearch;

        public DummySelectionStep(IAlgo algo, String id, int weight, AutoML autoML, boolean useSearch) {
            super(algo, id, weight, autoML);
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
}
