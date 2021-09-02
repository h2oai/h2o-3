package ai.h2o.automl;

import ai.h2o.automl.WorkAllocations.JobType;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.dummy.DummyStepsProvider;
import hex.Model;
import hex.ModelMetricsRegression;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static ai.h2o.automl.dummy.DummyStepsProvider.DummyModelSteps.*;
import static org.junit.Assert.*;
import static water.TestUtil.parseTestFile;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ModelingStepsExecutorTest {
    
    private static class TestingModelSteps extends DummyStepsProvider.DummyModelSteps {
        public TestingModelSteps(AutoML autoML) {
            super(autoML);
        }
        
        private ModelingStep[] defaults = {
                new TestingModelingStep("job_work", makeJob("dummy_job"), 42, 42, aml()),
                new TestingModelingStep("job_zero_work", makeJob("dummy_job"), 42, 0, aml()),
                new TestingModelingStep("job_no_work", makeJob("dummy_job"), 42, -1, aml()),
                new TestingModelingStep("no_job_work", null, 42, 42, aml()),
                new TestingModelingStepWithSubsteps("no_job_with_substeps", null, 42, 42, aml()),
                new TestingModelingStepWithSubsteps("job_with_substeps", makeJob("dummy_job"), 42, 42, aml()),
        };

        @Override
        protected ModelingStep[] getDefaultModels() {
            return defaults;
        }
    }

    private static Job makeJob(String name) {
        return new Job(Key.make(name), Model.class.getName(), "does nothing, not even started");
    }


    private AutoML aml;
    private Frame fr;

    @Before
    public void createAutoML() {
        DummyStepsProvider provider = new DummyStepsProvider();
        provider.modelStepsFactory = TestingModelSteps::new;
        ModelingStepsRegistry.registerProvider(provider);
        
        fr = parseTestFile("./smalldata/logreg/prostate_train.csv");
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        buildSpec.input_spec.training_frame = fr._key;
        buildSpec.input_spec.response_column = "CAPSULE";
        aml = new AutoML(buildSpec);
        DKV.put(aml);
    }

    @After
    public void cleanupAutoML() {
        if(aml!=null) aml.delete();
        if(fr!=null) fr.delete();
    }

    @Test
    public void test_start_stop() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start();
        assertTrue(executor._runCountdown.running());
        executor.stop();
        assertFalse(executor._runCountdown.running());
    }

    @Test
    public void test_submit_training_step_with_zero_work() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start();
        Job parentJob = makeJob("parent");
        boolean started = executor.submit(aml.session().getModelingStep(NAME, "job_zero_work"), parentJob);
        assertFalse(started);
        executor.stop();
    }

    @Test
    public void test_submit_training_step_with_no_allocated_work() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start();
        Job parentJob = makeJob("parent");
        boolean started = executor.submit(aml.session().getModelingStep(NAME, "job_no_work"), parentJob);
        assertFalse(started);
        executor.stop();
    }

    @Test
    public void test_submit_training_step_with_no_job() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start();
        Job parentJob = makeJob("parent");
        startParentJob(parentJob, j -> j._work < 100 && j._work > 0 && j.msec() < 5000);
        
        boolean started = executor.submit(aml.session().getModelingStep(NAME, "no_job_work"), parentJob);
        executor.stop();
        assertFalse(started);
        assertEquals(0.42, parentJob.progress(), 1e-6);  // parent job work should be filled with skipped work
        assertEquals(0, aml.leaderboard().getModelCount());
    }

    @Test
    public void test_submit_valid_training_step() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start();
        Job parentJob = makeJob("parent");
        startParentJob(parentJob, j -> j._work < 100 && j._work >= 42 && j.msec() < 5000);

        boolean started = executor.submit(aml.session().getModelingStep(NAME, "job_work"), parentJob);
        executor.stop();
        assertTrue(started);
        assertEquals(0.42, parentJob.progress(), 1e-6); // parent job work should be filled with executed work
        assertEquals(1, aml.leaderboard().getModelCount());
    }


    @Test
    public void test_submit_training_step_with_substeps_but_no_main_job() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start();
        Job parentJob = makeJob("parent");
        startParentJob(parentJob, j -> j._work < 100 && j._work >= 42 && j.msec() < 5000);
        boolean started = executor.submit(aml.session().getModelingStep(NAME, "no_job_with_substeps"), parentJob);
        assertTrue(started);
        executor.stop();
        assertEquals(2, aml.leaderboard().getModelCount());
    }

    @Test
    public void test_submit_training_step_with_substeps_and_main_job() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start();
        Job parentJob = makeJob("parent");
        startParentJob(parentJob, j -> j._work < 100 && j._work >= 42 && j.msec() < 5000);
        boolean started = executor.submit(aml.session().getModelingStep(NAME, "job_with_substeps"), parentJob);
        assertTrue(started);
        executor.stop();
        assertEquals(3, aml.leaderboard().getModelCount());
    }
    
    
    private static void startParentJob(Job parent, Predicate<Job> stoppingCondition) {
        parent.start(new H2O.H2OCountedCompleter() {
            @Override
            public void compute2() {
                while (true) {
                    try {
                        if (stoppingCondition.test(parent)) break;
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {}
                }
                tryComplete();
            }
        }, 100);
    }
    
    private static class TestingModelingStep extends ModelingStep {

        final Job _job;

        public TestingModelingStep(String id, Job job, int priorityGroup, int weight, AutoML autoML) {
            super(NAME, Algo.GBM, id, priorityGroup, weight, autoML);
            _job = job;
        }

        @Override
        protected JobType getJobType() {
            return JobType.ModelBuild;
        }

        @Override
        protected Work getAllocatedWork() {
            return _weight >= 0 ? makeWork(): null;
        }

        @Override
        protected Key makeKey(String name, boolean withCounter) {
            return Key.make(name);
        }

        @Override
        protected Job startJob() {
            if (_job==null) return null;
            return _job.start(new H2O.H2OCountedCompleter() {
                @Override
                public void compute2() {
                    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
                    GBMModel.GBMOutput output = new GBMModel.GBMOutput(new GBM(parms));
                    Model res = new GBMModel(_job._result, parms, output);
                    Frame fr = aml().getTrainingFrame();
                    output._training_metrics = new ModelMetricsRegression(res, fr, 1, 1, 1, 1, 1, 1, null);
                    DKV.put(_job._result, res);
                    tryComplete();
                }
            }, _weight);
        }
    }

    private static class TestingModelingStepWithSubsteps extends TestingModelingStep {

        public TestingModelingStepWithSubsteps(String id, Job job, int priorityGroup, int weight, AutoML autoML) {
            super(id, job, priorityGroup, weight, autoML);
        }

        @Override
        public Iterator<? extends ModelingStep> iterateSubSteps() {
            int workRatio = _job == null ? 2 : 3; // dividing the work in equal parts depending if there's a main job or not
            return Stream.of(
                    new TestingModelingStep("sub_step_1", makeJob("dummy_sub_step_job_1"), _priorityGroup, _weight / workRatio, aml()),
                    new TestingModelingStep("sub_step_2", makeJob("dummy_sub_step_job_2"), _priorityGroup, _weight / workRatio, aml())
            ).iterator();
        }
    }
}
