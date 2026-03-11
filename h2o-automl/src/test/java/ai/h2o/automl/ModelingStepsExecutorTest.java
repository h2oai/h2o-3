package ai.h2o.automl;

import ai.h2o.automl.StepResultState.ResultStatus;
import ai.h2o.automl.WorkAllocations.JobType;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.dummy.DummyStepsProvider;
import hex.Model;
import hex.ModelMetricsRegression;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.*;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import water.*;
import water.exceptions.H2OAutoMLException;
import water.fvec.Frame;
import water.junit.rules.ScopeTracker;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static ai.h2o.automl.ModelingStepsExecutor.DEFAULT_STATE_RESOLUTION_STRATEGY;
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
                new TestingModelingStepDummyModel("job_work", makeJob("dummy_job"), 42, 42, aml()),
                new TestingModelingStepDummyModel("job_zero_work", makeJob("dummy_job"), 42, 0, aml()),
                new TestingModelingStepDummyModel("job_no_work", makeJob("dummy_job"), 42, -1, aml()),
                new TestingModelingStepDummyModel("no_job_work", null, 42, 42, aml()),
                new TestingModelingStepWithException("cancelled_job", makeJob("dummy_job"), 42, 42, aml(), new Job.JobCancelledException()),
                new TestingModelingStepWithException("failed_job", makeJob("dummy_job"), 42, 42, aml(), new H2OAutoMLException("dummy")),
                new TestingModelingStepWithSubsteps("no_job_with_substeps", null, 42, 42, aml(), new ResultStatus[]{ResultStatus.success, ResultStatus.success}),
                new TestingModelingStepWithSubsteps("job_with_substeps", makeJob("dummy_job"), 42, 42, aml(), new ResultStatus[]{ResultStatus.success, ResultStatus.success}),
                new TestingModelingStepWithSubsteps("job_with_1_cancelled_substep", null, 42, 42, aml(), new ResultStatus[]{ResultStatus.success, ResultStatus.cancelled}),
                new TestingModelingStepWithSubsteps("job_with_1_failed_substep", null, 42, 42, aml(), new ResultStatus[]{ResultStatus.success, ResultStatus.failed, ResultStatus.cancelled}),
                new TestingModelingStepWithSubsteps("job_with_all_failed_substeps", null, 42, 42, aml(), new ResultStatus[]{ResultStatus.failed, ResultStatus.skipped, ResultStatus.cancelled, ResultStatus.failed}),
        };

        @Override
        protected ModelingStep[] getDefaultModels() {
            return defaults;
        }
    }

    private static Job makeJob(String name) {
        return new Job(Key.make(name), Model.class.getName(), name+" pretends to be real");
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

    @Rule
    public Timeout testTimeout = Timeout.seconds(5);
    
    @Rule
    public ScopeTracker scope = new ScopeTracker();


    @Test
    public void test_start_stop() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        assertTrue(executor._runCountdown.running());
        executor.stop();
        assertFalse(executor._runCountdown.running());
    }

    @Test
    public void test_submit_training_step_with_zero_work() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        Job parentJob = makeJob("parent");
        StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "job_zero_work"), parentJob);
        assertEquals(ResultStatus.skipped, state.status());
        executor.stop();
    }

    @Test
    public void test_submit_training_step_with_no_allocated_work() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        Job parentJob = makeJob("parent");
        StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "job_no_work"), parentJob);
        assertEquals(ResultStatus.skipped, state.status());
        executor.stop();
    }

    @Test
    public void test_submit_training_step_with_no_job() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        try (ParentJob parentJob = new ParentJob("parent", 1000)) {
          StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "no_job_work"), parentJob.get());
          executor.stop();
          assertEquals(ResultStatus.skipped, state.status());
          assertNull(state.error());
          assertEquals(0, state.subStates().size());
          assertEquals(0.42, parentJob.get().progress(), 1e-6);  // parent job work should be filled with skipped work
          assertEquals(0, aml.leaderboard().getModelCount());
        }
    }

    @Test
    public void test_submit_valid_training_step() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        try (ParentJob parentJob = new ParentJob("parent", 1000)) {
          StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "job_work"), parentJob.get());
          executor.stop();
          assertEquals(ResultStatus.success, state.status());
          assertNull(state.error());
          assertEquals(0, state.subStates().size());
          assertEquals(0.42, parentJob.get().progress(), 1e-6); // parent job work should be filled with executed work
          assertEquals(1, aml.leaderboard().getModelCount());
        }
    }
    
    @Test
    public void test_submit_cancelled_step() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        try (ParentJob parentJob = new ParentJob("parent", 1000)) {
          StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "cancelled_job"), parentJob.get());
          executor.stop();
          assertEquals(ResultStatus.cancelled, state.status());
          assertNull(state.error());
          assertEquals(0, state.subStates().size());
          assertEquals(0.42, parentJob.get().progress(), 1e-6); // parent job work should be filled with executed work
          assertEquals(0, aml.leaderboard().getModelCount());
        }
    }
    
    @Test
    public void test_submit_failed_step() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        try (ParentJob parentJob = new ParentJob("parent", 1000)) {
          StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "failed_job"), parentJob.get());
          executor.stop();
          assertEquals(ResultStatus.failed, state.status());
          assertTrue(state.error() instanceof H2OAutoMLException);
          assertEquals(0, state.subStates().size());
          assertEquals(0.42, parentJob.get().progress(), 1e-6); // parent job work should be filled with executed work
          assertEquals(0, aml.leaderboard().getModelCount());
        }
    }

    @Test
    public void test_submit_training_step_with_substeps_but_no_main_job() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        try (ParentJob parentJob = new ParentJob("parent", 1000)) {
          StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "no_job_with_substeps"), parentJob.get());
          assertEquals(ResultStatus.success, state.status());
          assertEquals(3, state.subStates().size());
          executor.stop();
          assertEquals(2, aml.leaderboard().getModelCount());
        }
    }

    @Test
    public void test_submit_training_step_with_substeps_and_main_job() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        try (ParentJob parentJob = new ParentJob("parent", 1000)) {
          StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "job_with_substeps"), parentJob.get());
          assertEquals(ResultStatus.success, state.status());
          assertEquals(3, state.subStates().size());
          executor.stop();
          assertEquals(3, aml.leaderboard().getModelCount());
        }
    }

    @Test
    public void test_submit_training_step_with_a_cancelled_substep() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        try (ParentJob parentJob = new ParentJob("parent", 1000)) {
          StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "job_with_1_cancelled_substep"), parentJob.get());
          assertEquals(ResultStatus.success, state.status());
          assertEquals(3, state.subStates().size());
          assertEquals(ResultStatus.success, state.subState(NAME+":sub_step_1").status());
          assertEquals(ResultStatus.cancelled, state.subState(NAME+":sub_step_2").status());
          executor.stop();
          assertEquals(1, aml.leaderboard().getModelCount());
        }
    }

    @Test
    public void test_submit_training_step_with_a_failed_substep() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        try (ParentJob parentJob = new ParentJob("parent", 1000)) {
          StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "job_with_1_failed_substep"), parentJob.get());
          assertEquals(ResultStatus.success, state.status());
          assertEquals(4, state.subStates().size());
          assertEquals(ResultStatus.success, state.subState(NAME+":sub_step_1").status());
          assertEquals(ResultStatus.failed, state.subState(NAME+":sub_step_2").status());
          assertEquals(ResultStatus.cancelled, state.subState(NAME+":sub_step_3").status());
          executor.stop();
          assertEquals(1, aml.leaderboard().getModelCount());
        }
    }

    @Test
    public void test_submit_training_step_with_no_success_substeps_optimistic() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, DEFAULT_STATE_RESOLUTION_STRATEGY);
        try (ParentJob parentJob = new ParentJob("parent", 1000)) {
          StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "job_with_all_failed_substeps"), parentJob.get());
          assertEquals(ResultStatus.cancelled, state.status());
          assertEquals(5, state.subStates().size());
          assertEquals(ResultStatus.failed, state.subState(NAME+":sub_step_1").status());
          assertEquals(ResultStatus.skipped, state.subState(NAME+":sub_step_2").status());
          assertEquals(ResultStatus.cancelled, state.subState(NAME+":sub_step_3").status());
          assertEquals(ResultStatus.failed, state.subState(NAME+":sub_step_4").status());
          executor.stop();
          assertEquals(0, aml.leaderboard().getModelCount());
        }
    }
    
    @Test
    public void test_submit_training_step_with_no_success_substeps_pessimistic() {
        ModelingStepsExecutor executor = new ModelingStepsExecutor(aml.leaderboard(), aml.eventLog(), aml._runCountdown);
        executor.start(10, StepResultState.Resolution.pessimistic);
        try (ParentJob parentJob = new ParentJob("parent", 1000)) {
          StepResultState state = executor.submit(aml.session().getModelingStep(NAME, "job_with_all_failed_substeps"), parentJob.get());
          assertEquals(ResultStatus.failed, state.status());
          assertEquals(5, state.subStates().size());
          assertEquals(ResultStatus.failed, state.subState(NAME+":sub_step_1").status());
          assertEquals(ResultStatus.skipped, state.subState(NAME+":sub_step_2").status());
          assertEquals(ResultStatus.cancelled, state.subState(NAME+":sub_step_3").status());
          assertEquals(ResultStatus.failed, state.subState(NAME+":sub_step_4").status());
          executor.stop();
          assertEquals(0, aml.leaderboard().getModelCount());
        }
    }



    private static void startParentJob(Job parent, Predicate<Job> stoppingCondition) {
        parent.start(new H2O.H2OCountedCompleter() {
            @Override
            public void compute2() {
                while (true) {
                    try {
                        if (stoppingCondition.test(parent)) parent.stop(); 
                        if (parent.stop_requested()) break;
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {}
                }
                tryComplete();
            }
        }, 100);
    }
    
    private static class ParentJob implements AutoCloseable {
      
      private final Job job;

      public ParentJob(String name, long timeoutInMillis) {
        job = makeJob(name);
        startParentJob(job, j -> j.msec() > timeoutInMillis);
      }

      @Override
      public void close() {
        job.stop(); 
        job.get();
      }
      
      public Job get() {
        return job;
      }
    }
    
    private static abstract class TestingModelingStep extends ModelingStep {

        final Job _job;

        public TestingModelingStep(String id, Job job, Algo algo, int priorityGroup, int weight, AutoML autoML) {
            super(NAME, algo, id, priorityGroup, weight, autoML);
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
                    doSth();
                    tryComplete();
                }
            }, _weight);
        }
        
        protected abstract void doSth();
    }
    
    private static class TestingModelingStepDummyModel extends TestingModelingStep {

        public TestingModelingStepDummyModel(String id, Job job, int priorityGroup, int weight, AutoML autoML) {
            super(id, job, Algo.GBM, priorityGroup, weight, autoML);
        }

        @Override
        protected void doSth() {
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            GBMModel.GBMOutput output = new GBMModel.GBMOutput(new GBM(parms));
            Model res = new GBMModel(_job._result, parms, output);
            Frame fr = aml().getTrainingFrame();
            output._training_metrics = new ModelMetricsRegression(res, fr, 1, 1, 1, 1, 1, 1, null);
            DKV.put(_job._result, res);
        }
    }

    private static class TestingModelingStepWithException extends TestingModelingStep {
        
        final RuntimeException _exception;
        public TestingModelingStepWithException(String id, Job job, int priorityGroup, int weight, AutoML autoML, RuntimeException exception) {
            super(id, job, Algo.GBM, priorityGroup, weight, autoML);
            _exception = exception;
        }

        @Override
        protected void doSth() {
            throw _exception;
        }
    }

    private static class TestingModelingStepWithSubsteps extends TestingModelingStepDummyModel {

        final ResultStatus[] _subStepStatuses;
        public TestingModelingStepWithSubsteps(String id, Job job, int priorityGroup, int weight, AutoML autoML, ResultStatus[] subStepStatuses) {
            super(id, job, priorityGroup, weight, autoML);
            _subStepStatuses = subStepStatuses;
        }

        @Override
        public Iterator<? extends ModelingStep> iterateSubSteps() {
            int workRatio = _job == null ? _subStepStatuses.length : _subStepStatuses.length+1; // dividing the work in equal parts depending if there's a main job or not
            AtomicInteger idx = new AtomicInteger();
            return Stream.of(_subStepStatuses).map(res -> makeSubStep(idx.incrementAndGet(), res, workRatio)).iterator();
        }
         private TestingModelingStep makeSubStep(int i, ResultStatus status, int workRatio) {
            switch (status) {
                case success: 
                    return new TestingModelingStepDummyModel("sub_step_"+i, makeJob("dummy_sub_step_job_"+i), 
                            _priorityGroup, _weight / workRatio, aml());
                case skipped:
                    return new TestingModelingStepDummyModel("sub_step_"+i, makeJob("dummy_sub_step_job_"+i),
                            _priorityGroup, 0, aml());
                case cancelled:
                    return new TestingModelingStepWithException("sub_step_"+i, makeJob("dummy_sub_step_job_"+i),
                            _priorityGroup, _weight / workRatio, aml(), new Job.JobCancelledException());
                case failed:
                    return new TestingModelingStepWithException("sub_step_"+i, makeJob("dummy_sub_step_job_"+i), 
                            _priorityGroup, _weight / workRatio, aml(), new H2OAutoMLException("dummy"));
            }
            return null;
         }
    }
}
