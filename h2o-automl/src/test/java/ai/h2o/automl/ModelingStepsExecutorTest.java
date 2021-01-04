package ai.h2o.automl;

import ai.h2o.automl.WorkAllocations.Work;
import hex.Model;
import hex.ModelMetricsRegression;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;

import java.util.Date;

import static org.junit.Assert.*;

public class ModelingStepsExecutorTest extends TestUtil {

    private AutoML aml;
    private Frame fr;

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Before
    public void createAutoML() {
        fr = parseTestFile("./smalldata/logreg/prostate_train.csv");
        AutoMLBuildSpec buildSpec = new AutoMLBuildSpec();
        buildSpec.input_spec.training_frame = fr._key;
        buildSpec.input_spec.response_column = "CAPSULE";
        aml = new AutoML(null, new Date(), buildSpec);
        DKV.put(aml);
    }

    @After
    public void cleanupAutoML() {
        if(aml!=null) aml.delete();
        if(fr!=null) fr.delete();
    }

    private Job makeJob(String name) {
        return new Job(Key.make(name), Model.class.getName(), "does nothing, not even started");
    }

    private ModelingStep makeStep(Job job, boolean withWork) {
        return new ModelingStep(Algo.GBM,"dummy", 42, aml) {
            Work work = withWork ? new Work(_id, _algo, WorkAllocations.JobType.ModelBuild, _weight) : null;
            @Override
            protected Work getAllocatedWork() {
                return work;
            }

            @Override
            protected Key makeKey(String name, boolean withCounter) {
                return Key.make(name);
            }

            @Override
            protected Work makeWork() {
                return work;
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
                        output._training_metrics = new ModelMetricsRegression(res, fr, 1, 1, 1, 1, 1, 1, null);
                        DKV.put(job._result, res);
                        tryComplete();
                    }
                }, _weight);
            }
        };
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
        boolean started = executor.submit(makeStep(makeJob("dummy"), false), parentJob);
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
        boolean started = executor.submit(makeStep(null, true), parentJob);
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

        boolean started = executor.submit(makeStep(makeJob("dummy"), true), parentJob);
        executor.stop();
        assertTrue(started);
        assertEquals(1, parentJob.progress(), 1e-6); // parent job should be auto-filled with remaining work
        assertEquals(1, aml.leaderboard().getModelCount());
    }
}
