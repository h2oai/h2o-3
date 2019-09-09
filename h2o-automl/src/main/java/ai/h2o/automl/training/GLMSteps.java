package ai.h2o.automl.training;

import ai.h2o.automl.*;
import hex.glm.GLMModel;
import hex.glm.GLMModel.GLMParameters;
import water.Job;


public class GLMSteps extends TrainingSteps {

    public static class Provider implements TrainingStepsProvider<GLMSteps> {
        @Override
        public String getName() {
            return Algo.GLM.name();
        }

        @Override
        public Class<GLMSteps> getStepsClass() {
            return GLMSteps.class;
        }
    }

    static abstract class GLMModelStep extends TrainingStep.ModelStep<GLMModel> {

        GLMModelStep(String id, AutoML autoML) {
            super(Algo.GLM, id, autoML);
        }

        GLMParameters prepareModelParameters() {
            GLMParameters glmParameters = new GLMParameters();
            glmParameters._lambda_search = true;
            glmParameters._family =
                    _aml.getResponseColumn().isBinary() && !(_aml.getResponseColumn().isNumeric()) ? GLMParameters.Family.binomial
                            : _aml.getResponseColumn().isCategorical() ? GLMParameters.Family.multinomial
                            : GLMParameters.Family.gaussian;  // TODO: other continuous distributions!
            return glmParameters;
        }
    }


    private TrainingStep[] defaults = new GLMModelStep[] {
            new GLMModelStep("def_1", _aml) {
                @Override
                protected Job makeJob() {
                    GLMParameters glmParameters = prepareModelParameters();
                    glmParameters._alpha = new double[] {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};
                    glmParameters._missing_values_handling = GLMParameters.MissingValuesHandling.MeanImputation;

                    return trainModel(glmParameters);
                }
            },
    };

    private TrainingStep[] grids = new TrainingStep[] {
            /*
            new GLMStep("grid_1", _aml) {
                @Override
                protected Job makeJob() {
                    GLMParameters glmParameters = prepareModelParameters();
                    glmParameters._alpha = new double[] {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};

                    Map<String, Object[]> searchParams = new HashMap<>();
                    // NOTE: removed MissingValuesHandling.Skip for now because it's crashing.  See https://0xdata.atlassian.net/browse/PUBDEV-4974
                    searchParams.put("_missing_values_handling", new GLMParameters.MissingValuesHandling[] {
                            GLMParameters.MissingValuesHandling.MeanImputation,
//                            GLMParameters.MissingValuesHandling.Skip
                    });

                    return hyperparameterSearch(glmParameters, searchParams);
                }
            },
             */
    };

    public GLMSteps(AutoML autoML) {
        super(autoML);
    }

    @Override
    protected TrainingStep[] getDefaultModels() {
        return defaults;
    }

    @Override
    protected TrainingStep[] getGrids() {
        return grids;
    }
}
