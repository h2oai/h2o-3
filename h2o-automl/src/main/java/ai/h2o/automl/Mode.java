package ai.h2o.automl;

public enum Mode {
    explore() {
        private final StepDefinition[] MODELING_PLAN = {
                new StepDefinition(Algo.XGBoost.name(), StepDefinition.Alias.defaults),
                new StepDefinition(Algo.GLM.name(), StepDefinition.Alias.defaults),
                new StepDefinition(Algo.DRF.name(), new String[]{ "def_1" }),
                new StepDefinition(Algo.GBM.name(), StepDefinition.Alias.defaults),
                new StepDefinition(Algo.DeepLearning.name(), StepDefinition.Alias.defaults),
                new StepDefinition(Algo.DRF.name(), new String[]{ "XRT" }),
                new StepDefinition(Algo.XGBoost.name(), StepDefinition.Alias.grids),
                new StepDefinition(Algo.GBM.name(), StepDefinition.Alias.grids),
                new StepDefinition(Algo.DeepLearning.name(), StepDefinition.Alias.grids),
                new StepDefinition(Algo.GBM.name(), new String[]{ "lr_annealing" }),
                new StepDefinition(Algo.XGBoost.name(), new String[]{ "lr_search" }),
                new StepDefinition(Algo.StackedEnsemble.name(), StepDefinition.Alias.defaults),
        };
        @Override
        public StepDefinition[] getModelingPlan() {
            return MODELING_PLAN;
        }
    },
    compete() {
        private final StepDefinition[] MODELING_PLAN = {
                new StepDefinition(Algo.XGBoost.name(), StepDefinition.Alias.defaults),
                new StepDefinition(Algo.GLM.name(), StepDefinition.Alias.defaults),
                new StepDefinition(Algo.DRF.name(), new String[]{ "def_1" }),
                new StepDefinition(Algo.GBM.name(), StepDefinition.Alias.defaults),
                new StepDefinition(Algo.DeepLearning.name(), StepDefinition.Alias.defaults),
                new StepDefinition(Algo.DRF.name(), new String[]{ "XRT" }),
                new StepDefinition(Algo.XGBoost.name(), StepDefinition.Alias.grids),
                new StepDefinition(Algo.GBM.name(), StepDefinition.Alias.grids),
                new StepDefinition(Algo.GBM.name(), new String[]{ "lr_annealing" }),
                new StepDefinition(Algo.XGBoost.name(), new String[]{ "lr_search" }),
                new StepDefinition(Algo.StackedEnsemble.name(), StepDefinition.Alias.defaults),
        };
        @Override
        public StepDefinition[] getModelingPlan() {
            return MODELING_PLAN;
        }
        
    },
//    interpret
    ;
    
    public abstract StepDefinition[] getModelingPlan();
}
