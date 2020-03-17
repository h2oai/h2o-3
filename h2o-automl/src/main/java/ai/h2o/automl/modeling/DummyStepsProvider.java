package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.events.EventLogEntry;
import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import water.*;
import water.fvec.Frame;

import static ai.h2o.automl.ModelingStep.ModelStep.DEFAULT_MODEL_TRAINING_WEIGHT;


public class DummyStepsProvider
        implements ModelingStepsProvider<DummyStepsProvider.DummySteps> {

    static class DummySteps extends ModelingSteps {
        static abstract class DummySleepStep extends ModelingStep.ModelStep<DRFModel> {
            DummySleepStep(String id, int weight, AutoML autoML) {
                super(Algo.DRF, id, weight, autoML);
            }
        }

        public DummySteps(AutoML autoML) {
            super(autoML);
        }

        DRFModel.DRFParameters prepareModelParameters() {
            DRFModel.DRFParameters params = new DRFModel.DRFParameters();
            params._train = aml().getTrainingFrame()._key;
            if (null != aml().getValidationFrame())
                params._valid = aml().getValidationFrame()._key;

            AutoMLBuildSpec buildSpec = aml().getBuildSpec();
            params._response_column = buildSpec.input_spec.response_column;
            params._ignored_columns = buildSpec.input_spec.ignored_columns;
            return params;
        }


        private ModelingStep[] defaults = new ModelingStep[]{
                new DummySteps.DummySleepStep("dummy_sleep", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {
                    @Override
                    protected Job<DRFModel> startJob() {
                        double maxRuntimeSecs = aml()
                                .getBuildSpec()
                                .build_control
                                .stopping_criteria
                                .max_runtime_secs_per_model();
                        maxRuntimeSecs = Math.min(maxRuntimeSecs, aml().timeRemainingMs() / 1e3);
                        long sleepIterations = (long) maxRuntimeSecs * 10;
                        Key key = makeKey("dummy_sleep", true);
                        Job<DRFModel> job = new Job(key, Model.class.getName(), "does nothing, just sleeps");
                        return job.start(new H2O.H2OCountedCompleter() {
                            @Override
                            public void compute2() {
                                try {
                                    for (int i = 0; i < sleepIterations; i++) {
                                        // Pretend some work
                                        Thread.sleep(100);
                                        // Update the status
                                        job.update(1);
                                    }
                                    DRFModel.DRFOutput out = new DRFModel.DRFOutput(new DRF(prepareModelParameters()));
                                    DRFModel model = new DRFModel(job._result, prepareModelParameters(), out);
                                    Frame train = aml().getTrainingFrame();
                                    model._output._training_metrics = model
                                            .makeMetricBuilder(new String[0])
                                            .makeModelMetrics(model, train, train, train);
                                    DKV.put(model);
                                    tryComplete();
                                } catch (InterruptedException e) {
                                    aml().eventLog().info(EventLogEntry.Stage.ModelTraining, "DummyModelStep sleep interrupted.");
                                    Keyed.remove(job._result);
                                }
                            }
                        }, sleepIterations);
                    }
                }
        };

        @Override
        protected ModelingStep[] getDefaultModels() {
            return defaults;
        }
    }


    @Override
    public String getName() {
        return "Dummy";
    }

    @Override
    public DummySteps newInstance(AutoML aml) {
        return new DummySteps(aml);
    }
}
