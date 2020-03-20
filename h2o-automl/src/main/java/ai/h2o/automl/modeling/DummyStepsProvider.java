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

        private static DRFModel.DRFParameters prepareModelParameters(AutoML aml) {
            DRFModel.DRFParameters params = new DRFModel.DRFParameters();
            params._train = aml.getTrainingFrame()._key;
            if (null != aml.getValidationFrame())
                params._valid = aml.getValidationFrame()._key;

            AutoMLBuildSpec buildSpec = aml.getBuildSpec();
            params._response_column = buildSpec.input_spec.response_column;
            params._ignored_columns = buildSpec.input_spec.ignored_columns;
            params._fold_column = buildSpec.input_spec.fold_column;
            params._weights_column = buildSpec.input_spec.weights_column;

            if (buildSpec.input_spec.fold_column == null) {
                params._nfolds = buildSpec.build_control.nfolds;
                if (buildSpec.build_control.nfolds > 1) {
                    params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
                }
            }

            if (buildSpec.build_control.balance_classes) {
                params._balance_classes = buildSpec.build_control.balance_classes;
                params._class_sampling_factors = buildSpec.build_control.class_sampling_factors;
                params._max_after_balance_size = buildSpec.build_control.max_after_balance_size;
            }

            params._keep_cross_validation_predictions = true;
            params._keep_cross_validation_models = buildSpec.build_control.keep_cross_validation_models;
            params._keep_cross_validation_fold_assignment = buildSpec.build_control.nfolds != 0 && buildSpec.build_control.keep_cross_validation_fold_assignment;
            params._export_checkpoints_dir = buildSpec.build_control.export_checkpoints_dir;

            return params;
        }

        private static void makeDummyModel(AutoML aml, Job<DRFModel> job) {
            DRFModel.DRFParameters params = prepareModelParameters(aml);
            DRFModel model = new DRFModel(job._result, params, new DRFModel.DRFOutput(new DRF(params)));
            Frame train = aml.getTrainingFrame();
            model._output._training_metrics = model
                    .makeMetricBuilder(new String[0])
                    .makeModelMetrics(model, train, train, train);

            Frame predictions = train.subframe(new String[]{model._output.responseName(),}).deepCopy("dummy_predictions");
            predictions.add("probabilities", predictions.vec(0));
            predictions.add("predict", predictions.vec(0));
            model._output._cross_validation_holdout_predictions_frame_id = predictions._key;

            DKV.put(predictions);
            DKV.put(model);
        }

        static abstract class DummySleepStep extends ModelingStep.ModelStep<DRFModel> {
            DummySleepStep(String id, int weight, AutoML autoML) {
                super(Algo.DRF, id, weight, autoML);
            }

            @Override
            protected Job<DRFModel> startJob() {
                //FIXME: This part will use max_runtime_secs provided by aml {
                double maxRuntimeSecs = aml()
                        .getBuildSpec()
                        .build_control
                        .stopping_criteria
                        .max_runtime_secs_per_model();
                // if max_runtime_secs_per_model is not specified (or is negative) try to use max_runtime_secs_per_model
                if (maxRuntimeSecs <= 0) {
                    maxRuntimeSecs = aml().getBuildSpec().build_control.stopping_criteria.max_runtime_secs();
                }
                // if both max_runtime_secs{,_per_model} are not specified or negative use weight
                if (maxRuntimeSecs <= 0) {
                    maxRuntimeSecs = _weight;
                }
                maxRuntimeSecs = Math.min(maxRuntimeSecs, aml().timeRemainingMs() / 1e3);
                // }

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
                            makeDummyModel(aml(), job);
                            tryComplete();
                        } catch (InterruptedException e) {
                            aml().eventLog().info(EventLogEntry.Stage.ModelTraining, "DummyStep sleep interrupted.");
                            // remove "partially trained" model, e.g., without metrics
                            Keyed.remove(job._result);
                        }
                    }
                }, sleepIterations);
            }
        }

        public DummySteps(AutoML autoML) {
            super(autoML);
        }

        private ModelingStep[] defaults = new ModelingStep[]{
                new DummySteps.DummySleepStep("dummy_sleep", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {
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
