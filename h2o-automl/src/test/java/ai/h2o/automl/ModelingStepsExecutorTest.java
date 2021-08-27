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
                makeStep("job_work", makeJob("dummy_job"), true, aml()),
                makeStep("job_no_work", makeJob("dummy_job"), false, aml()),
                makeStep("no_job_work", null, true, aml()),
        };

        @Override
        protected ModelingStep[] getDefaultModels() {
            return defaults;
        }
    }
    
    private static ModelingStep makeStep(String id, Job job, boolean withWork, AutoML aml) {
        return new ModelingStep(NAME, Algo.GBM, id, 42, 42, aml) {

            @Override
            protected JobType getJobType() {
                return JobType.ModelBuild;
            }

            @Override
            protected Work getAllocatedWork() {
                return withWork ? makeWork() : null;
            }

            @Override
            protected Key makeKey(String name, boolean withCounter) {
                return Key.make(name);
            }

            @Override
            protected Job startJob() {
                if (job == null) return null;
                return job.start(new H2O.H2OCountedCompleter() {
                    @Override
                    public void compute2() {
                        GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
                        GBMModel.GBMOutput output = new GBMModel.GBMOutput(new GBM(parms));
                        Model res = new GBMModel(job._result, parms, output);
                        Frame fr = aml.getTrainingFrame();
                        output._training_metrics = new ModelMetricsRegression(res, fr, 1, 1, 1, 1, 1, 1, null);
                        DKV.put(job._result, res);
                        tryComplete();
                    }
                }, _weight);
            }
        };
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
        parentJob.start(new H2O.H2OCountedCompleter() {
            @Override
            public void compute2() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                tryComplete();
            }
        }, 100);
        boolean started = executor.submit(aml.session().getModelingStep(NAME, "no_job_work"), parentJob);
        executor.stop();
        assertFalse(started);
        assertEquals(0.42, parentJob.progress(), 1e-6);
        assertEquals(0, aml.leaderboard().getModelCount());
    }

    @Test
    public void test_submit_valid_training_step() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start();
        Job parentJob = makeJob("parent");
        parentJob.start(new H2O.H2OCountedCompleter() {
            @Override
            public void compute2() {
                while (true) {
                    try {
                        if (parentJob._work >= 42) break;
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) { }
                }
                tryComplete();
            }
        }, 100);

        boolean started = executor.submit(aml.session().getModelingStep(NAME, "job_work"), parentJob);
        executor.stop();
        assertTrue(started);
        assertEquals(1, parentJob.progress(), 1e-6); // parent job should be auto-filled with remaining work
        assertEquals(1, aml.leaderboard().getModelCount());
    }
}
