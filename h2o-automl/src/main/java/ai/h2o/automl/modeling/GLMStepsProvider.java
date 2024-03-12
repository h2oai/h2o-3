package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.preprocessing.PreprocessingConfig;
import ai.h2o.automl.preprocessing.TargetEncoding;
import hex.Model;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMParameters;


public class GLMStepsProvider
        implements ModelingStepsProvider<GLMStepsProvider.GLMSteps>
                 , ModelParametersProvider<GLMParameters> {

    public static class GLMSteps extends ModelingSteps {

        static final String NAME = Algo.GLM.name();
        
        static abstract class GLMModelStep extends ModelingStep.ModelStep<GLMModel> {

            GLMModelStep(String id, AutoML autoML) {
                super(NAME, Algo.GLM, id, autoML);
            }

            @Override
            protected void setStoppingCriteria(Model.Parameters parms, Model.Parameters defaults) {
                // disabled as we're using lambda search
            }

            public GLMParameters prepareModelParameters() {
                GLMParameters params = new GLMParameters();
                params._lambda_search = true;
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


        private final ModelingStep[] defaults = new GLMModelStep[] {
                new GLMModelStep("def_1", aml()) {
                    @Override
                    public GLMParameters prepareModelParameters() {
                        GLMParameters params = super.prepareModelParameters();
                        params._alpha = new double[] {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};
                        params._missing_values_handling = GLMParameters.MissingValuesHandling.MeanImputation;
                        return params;
                    }
                },
        };

        public GLMSteps(AutoML autoML) {
            super(autoML);
        }

        @Override
        public String getProvider() {
            return NAME;
        }

        @Override
        protected ModelingStep[] getDefaultModels() {
            return defaults;
        }
    }

    @Override
    public String getName() {
        return GLMSteps.NAME;
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

