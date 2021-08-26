package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.preprocessing.PreprocessingConfig;
import ai.h2o.automl.preprocessing.TargetEncoding;
import hex.deeplearning.DeepLearningModel;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;

import java.util.HashMap;
import java.util.Map;


public class DeepLearningStepsProvider
        implements ModelingStepsProvider<DeepLearningStepsProvider.DeepLearningSteps>
                 , ModelParametersProvider<DeepLearningParameters> {

    public static class DeepLearningSteps extends ModelingSteps {

        static final String NAME = Algo.DeepLearning.name();
        
        static abstract class DeepLearningModelStep extends ModelingStep.ModelStep<DeepLearningModel> {
            public DeepLearningModelStep(String id, AutoML autoML) {
                super(NAME, Algo.DeepLearning, id, autoML);
            }
            
            @Override
            protected PreprocessingConfig getPreprocessingConfig() {
                //TE useless for DNN
                PreprocessingConfig config = super.getPreprocessingConfig();
                config.put(TargetEncoding.CONFIG_PREPARE_CV_ONLY, aml().isCVEnabled());
                return config;
            }
        }

        static abstract class DeepLearningGridStep extends ModelingStep.GridStep<DeepLearningModel> {

            DeepLearningGridStep(String id, AutoML autoML) {
                super(NAME, Algo.DeepLearning, id, autoML);
            }

            public DeepLearningParameters prepareModelParameters() {
                DeepLearningParameters params = new DeepLearningParameters();
                params._epochs = 10000; // early stopping takes care of epochs - no need to tune!
                params._adaptive_rate = true;
                params._activation = DeepLearningParameters.Activation.RectifierWithDropout;
                return params;
            }
            
            @Override
            protected PreprocessingConfig getPreprocessingConfig() {
                //TE useless for DNN
                PreprocessingConfig config = super.getPreprocessingConfig();
                config.put(TargetEncoding.CONFIG_PREPARE_CV_ONLY, aml().isCVEnabled());
                return config;
            }

            public Map<String, Object[]> prepareSearchParameters() {
                Map<String, Object[]> searchParams = new HashMap<>();
                searchParams.put("_rho", new Double[] { 0.9, 0.95, 0.99 });
                searchParams.put("_epsilon", new Double[] { 1e-6, 1e-7, 1e-8, 1e-9 });
                searchParams.put("_input_dropout_ratio", new Double[] { 0.0, 0.05, 0.1, 0.15, 0.2 });
                return searchParams;
            }
        }


        private final ModelingStep[] defaults = new DeepLearningModelStep[] {
                new DeepLearningModelStep("def_1", aml()) {
                    @Override
                    public DeepLearningParameters prepareModelParameters() {
                        DeepLearningParameters params = new DeepLearningParameters();  // don't use common params for default DL
                        params._hidden = new int[]{ 10, 10, 10 };
                        return params;
                    }
                },
        };

        private final ModelingStep[] grids = new DeepLearningGridStep[] {
                new DeepLearningGridStep("grid_1", aml()) {
                    @Override
                    public Map<String, Object[]> prepareSearchParameters() {
                        Map<String, Object[]> searchParams = super.prepareSearchParameters();
                        searchParams.put("_hidden", new Integer[][] {
                                {  20 },
                                {  50 },
                                { 100 }
                        });
                        searchParams.put("_hidden_dropout_ratios", new Double[][] {
                                { 0.0 },
                                { 0.1 },
                                { 0.2 },
                                { 0.3 },
                                { 0.4 },
                                { 0.5 }
                        });
                        return searchParams;
                    }
                },
                new DeepLearningGridStep("grid_2", aml()) {
                    @Override
                    public Map<String, Object[]> prepareSearchParameters() {
                        Map<String, Object[]> searchParams = super.prepareSearchParameters();
                        searchParams.put("_hidden", new Integer[][] {
                                {  20,  20 },
                                {  50,  50 },
                                { 100, 100 }
                        });
                        searchParams.put("_hidden_dropout_ratios", new Double[][] {
                                { 0.0, 0.0 },
                                { 0.1, 0.1 },
                                { 0.2, 0.2 },
                                { 0.3, 0.3 },
                                { 0.4, 0.4 },
                                { 0.5, 0.5 }
                        });
                        return searchParams;
                    }
                },
                new DeepLearningGridStep("grid_3", aml()) {
                    @Override
                    public Map<String, Object[]> prepareSearchParameters() {
                        Map<String, Object[]> searchParams = super.prepareSearchParameters();
                        searchParams.put("_hidden", new Integer[][] {
                                {  20,  20,  20 },
                                {  50,  50,  50 },
                                { 100, 100, 100 }
                        });
                        searchParams.put("_hidden_dropout_ratios", new Double[][] {
                                { 0.0, 0.0, 0.0 },
                                { 0.1, 0.1, 0.1 },
                                { 0.2, 0.2, 0.2 },
                                { 0.3, 0.3, 0.3 },
                                { 0.4, 0.4, 0.4 },
                                { 0.5, 0.5, 0.5 }
                        });
                        return searchParams;
                    }
                },
        };

        public DeepLearningSteps(AutoML autoML) {
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

        @Override
        protected ModelingStep[] getGrids() {
            return grids;
        }
    }

    @Override
    public String getName() {
        return DeepLearningSteps.NAME;
    }

    @Override
    public DeepLearningSteps newInstance(AutoML aml) {
        return new DeepLearningSteps(aml);
    }

    @Override
    public DeepLearningParameters newDefaultParameters() {
        return new DeepLearningParameters();
    }
}

