package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.preprocessing.PreprocessingConfig;
import ai.h2o.automl.preprocessing.PreprocessingStepDefinition;
import ai.h2o.automl.preprocessing.TargetEncoding;
import hex.Model;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMParameters;
import water.Job;

import static ai.h2o.automl.ModelingStep.ModelStep.DEFAULT_MODEL_TRAINING_WEIGHT;


public class GLMStepsProvider
        implements ModelingStepsProvider<GLMStepsProvider.GLMSteps>
                 , ModelParametersProvider<GLMParameters> {

    public static class GLMSteps extends ModelingSteps {

        static abstract class GLMModelStep extends ModelingStep.ModelStep<GLMModel> {

            GLMModelStep(String id, int weight, AutoML autoML) {
                super(Algo.GLM, id, weight, autoML);
            }

            @Override
            protected void setStoppingCriteria(Model.Parameters parms, Model.Parameters defaults) {
                // disabled as we're using lambda search
            }

            GLMParameters prepareModelParameters() {
                GLMParameters params = new GLMParameters();
                params._lambda_search = true;
                params._family =
                        aml().getResponseColumn().isBinary() && !(aml().getResponseColumn().isNumeric()) ? GLMParameters.Family.binomial
                                : aml().getResponseColumn().isCategorical() ? GLMParameters.Family.multinomial
                                : GLMParameters.Family.gaussian;  // TODO: other continuous distributions!
                return params;
            }
            
            @Override
            protected PreprocessingConfig getPreprocessingConfig() {
                //GLM (the exception as usual) doesn't support targetencoding if CV is enabled
                // because it is initializing its lambdas + other params before CV (preventing changes in train frame during CV).
                PreprocessingConfig config = super.getPreprocessingConfig();
                config.put(TargetEncoding.CONFIG_PREPARE_CV_ONLY, aml().isCVEnabled()); 
                return config;
            }
        }


        private ModelingStep[] defaults = new GLMModelStep[] {
                new GLMModelStep("def_1", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {
                    @Override
                    protected Job<GLMModel> startJob() {
                        GLMParameters params = prepareModelParameters();
                        params._alpha = new double[] {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};
                        params._missing_values_handling = GLMParameters.MissingValuesHandling.MeanImputation;

                        return trainModel(params);
                    }
                },
        };

        public GLMSteps(AutoML autoML) {
            super(autoML);
        }

        @Override
        protected ModelingStep[] getDefaultModels() {
            return defaults;
        }

    }

    @Override
    public String getName() {
        return Algo.GLM.name();
    }

    @Override
    public GLMSteps newInstance(AutoML aml) {
        return new GLMSteps(aml);
    }

    @Override
    public GLMParameters newDefaultParameters() {
        return new GLMParameters();
    }
}

