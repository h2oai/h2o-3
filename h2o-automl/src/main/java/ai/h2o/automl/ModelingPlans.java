package ai.h2o.automl;

import ai.h2o.automl.StepDefinition.Step;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static ai.h2o.automl.ModelingStep.GridStep.DEFAULT_GRID_GROUP;
import static ai.h2o.automl.ModelingStep.GridStep.DEFAULT_GRID_TRAINING_WEIGHT;
import static ai.h2o.automl.ModelingStep.ModelStep.DEFAULT_MODEL_GROUP;
import static ai.h2o.automl.ModelingStep.ModelStep.DEFAULT_MODEL_TRAINING_WEIGHT;

final class ModelingPlans {

    /**
     * Plan reflecting the behaviour of H2O AutoML prior v3.34 as close as possible.
     * 
     * Keeping it mainly for reference and for tests.
     */
    final static StepDefinition[] ONE_LAYERED = {
            new StepDefinition(Algo.XGBoost.name(), StepDefinition.Alias.defaults),
            new StepDefinition(Algo.GLM.name(), StepDefinition.Alias.defaults),
            new StepDefinition(Algo.DRF.name(), "def_1"),
            new StepDefinition(Algo.GBM.name(), StepDefinition.Alias.defaults),
            new StepDefinition(Algo.DeepLearning.name(), StepDefinition.Alias.defaults),
            new StepDefinition(Algo.DRF.name(), "XRT"),
            new StepDefinition(Algo.XGBoost.name(), 
                    new Step("grid_1", DEFAULT_MODEL_GROUP, 3*DEFAULT_GRID_TRAINING_WEIGHT)),
            new StepDefinition(Algo.GBM.name(), 
                    new Step("grid_1", DEFAULT_MODEL_GROUP, 2*DEFAULT_GRID_TRAINING_WEIGHT)),
            new StepDefinition(Algo.DeepLearning.name(),
                    new Step("grid_1", DEFAULT_MODEL_GROUP, DEFAULT_GRID_TRAINING_WEIGHT/2),
                    new Step("grid_2", DEFAULT_MODEL_GROUP, DEFAULT_GRID_TRAINING_WEIGHT/2),
                    new Step("grid_3", DEFAULT_MODEL_GROUP, DEFAULT_GRID_TRAINING_WEIGHT/2)),
            new StepDefinition(Algo.GBM.name(), 
                    new Step("lr_annealing", DEFAULT_MODEL_GROUP, DEFAULT_MODEL_TRAINING_WEIGHT)),
            new StepDefinition(Algo.XGBoost.name(), 
                    new Step("lr_search", DEFAULT_MODEL_GROUP, DEFAULT_GRID_TRAINING_WEIGHT)),
            new StepDefinition(Algo.StackedEnsemble.name(), 
                    new Step("best_of_family", DEFAULT_MODEL_GROUP, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("all", DEFAULT_MODEL_GROUP, DEFAULT_MODEL_TRAINING_WEIGHT)),
    };


    /**
     * A simple improvement on the one-layered version using mainly default settings:
     * <ul>
     *     <li>the first layer attempts to train all the base models.</li>
     *     <li>if this layer completes, a second layer trains all the grids.</li>
     *     <li>an optional 3rd layer is trained if exploitation ratio is on.</li>
     *     <li>2 SEs are trained at the end of each layer</li>
     * </ul>
     * 
     * Keeping this as an example of simple plan, mainly used for tests.
     */
    final static StepDefinition[] TWO_LAYERED = {
            new StepDefinition(Algo.XGBoost.name(), StepDefinition.Alias.defaults),
            new StepDefinition(Algo.GLM.name(), StepDefinition.Alias.defaults),
            new StepDefinition(Algo.DRF.name(), StepDefinition.Alias.defaults),
            new StepDefinition(Algo.GBM.name(), StepDefinition.Alias.defaults),
            new StepDefinition(Algo.DeepLearning.name(), StepDefinition.Alias.defaults),
            new StepDefinition(Algo.XGBoost.name(), StepDefinition.Alias.grids),
            new StepDefinition(Algo.GBM.name(), StepDefinition.Alias.grids),
            new StepDefinition(Algo.DeepLearning.name(), StepDefinition.Alias.grids),
            new StepDefinition(Algo.GBM.name(), 
                    new Step("lr_annealing", DEFAULT_GRID_GROUP+1, Step.DEFAULT_WEIGHT)),
            new StepDefinition(Algo.XGBoost.name(), 
                    new Step("lr_search", DEFAULT_GRID_GROUP+1, Step.DEFAULT_WEIGHT)),
            new StepDefinition(Algo.StackedEnsemble.name(), StepDefinition.Alias.defaults),
    };

    /**
     * a multi-layered plan:
     * <ol>
     *     <li>a short first layer with only 3 base models to be able to produce at least a few decent models 
     *     on larger datasets if time budget is small,
     *     followed by an SE of those models.</li>
     *     <li>another layer with more base models if all the models in the first layer were able to converge
     *     + SEs for that layer/li>
     *     <li>another layer with the remaining base models + SEs</li>
     *     <li>another layer with the usually fast and best performing grids + SEs</li>
     *     <li>another layer with the remaining grids + SEs</li>
     *     <li>another layer doing a learning_rate search on the best GBM+XGB and adding some optional SEs</li>
     *     <li>another layer with more optional SEs</li>
     *     <li>a final layer resuming the best 2 grids so far, this time running without grid early stopping, 
     *     and followed by 2 final SEs</li>
     * </ol>
     * 
     */
    final static StepDefinition[] TEN_LAYERED = {
            // order of step definitions and steps defines the order of steps in the same priority group.
            new StepDefinition(Algo.XGBoost.name(), 
                    new Step("def_2", 1, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_1", 2, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_3", 3, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("grid_1", 4, 3*DEFAULT_GRID_TRAINING_WEIGHT),
                    new Step("lr_search", 6, DEFAULT_GRID_TRAINING_WEIGHT)
            ),
            new StepDefinition(Algo.GLM.name(), 
                    new Step("def_1", 1, DEFAULT_MODEL_TRAINING_WEIGHT)
            ),
            new StepDefinition(Algo.DRF.name(), 
                    new Step("def_1", 2, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("XRT", 3, DEFAULT_MODEL_TRAINING_WEIGHT)
            ),
            new StepDefinition(Algo.GBM.name(), 
                    new Step("def_5", 1, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_2", 2, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_3", 2, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_4", 2, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_1", 3, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("grid_1", 4, 2*DEFAULT_GRID_TRAINING_WEIGHT),
                    new Step("lr_annealing", 6, DEFAULT_MODEL_TRAINING_WEIGHT)
            ),
            new StepDefinition(Algo.DeepLearning.name(), 
                    new Step("def_1", 3, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("grid_1", 4, DEFAULT_GRID_TRAINING_WEIGHT),
                    new Step("grid_2", 5, DEFAULT_GRID_TRAINING_WEIGHT),
                    new Step("grid_3", 5, DEFAULT_GRID_TRAINING_WEIGHT)
            ),
            new StepDefinition("completion", 
                    new Step("resume_best_grids", 10, 2*DEFAULT_GRID_TRAINING_WEIGHT)
            ),
            // generates BoF and All SE for each group, but we prefer to customize instances and weights below 
  //          new StepDefinition(Algo.StackedEnsemble.name(), StepDefinition.Alias.defaults), 
            new StepDefinition(Algo.StackedEnsemble.name(), Stream.of(new Step[][] {
                    IntStream.rangeClosed(1, 5).mapToObj(group -> // BoF should be fast, giving it half-budget for optimization.
                            new Step("best_of_family_"+group, group, DEFAULT_MODEL_TRAINING_WEIGHT/2)
                    ).toArray(Step[]::new),
                    IntStream.rangeClosed(2, 5).mapToObj(group -> // starts at 2 as we don't need an ALL SE for first group.
                            new Step("all_"+group, group, DEFAULT_MODEL_TRAINING_WEIGHT)
                    ).toArray(Step[]::new),
                    {
                            new Step("monotonic", 6, DEFAULT_MODEL_TRAINING_WEIGHT),
                            new Step("best_of_family_gbm", 6, DEFAULT_MODEL_TRAINING_WEIGHT),
                            new Step("all_gbm", 7, DEFAULT_MODEL_TRAINING_WEIGHT),
                            new Step("best_of_family_xglm", 8, DEFAULT_MODEL_TRAINING_WEIGHT),
                            new Step("all_xglm", 8, DEFAULT_MODEL_TRAINING_WEIGHT),
                            new Step("best_of_family", 10, DEFAULT_MODEL_TRAINING_WEIGHT),
                            new Step("best_N", 10, DEFAULT_MODEL_TRAINING_WEIGHT),
                    }
            }).flatMap(Stream::of).toArray(Step[]::new)),
    };
    
    final static StepDefinition[] REPRODUCIBLE = {
            // order of step definitions and steps defines the order of steps in the same priority group.
            new StepDefinition(Algo.XGBoost.name(),
                    new Step("def_2", 1, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_1", 2, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_3", 3, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("grid_1", 4, 3*DEFAULT_GRID_TRAINING_WEIGHT),
                    new Step("lr_search", 7, DEFAULT_GRID_TRAINING_WEIGHT)
            ),
            new StepDefinition(Algo.GLM.name(),
                    new Step("def_1", 1, DEFAULT_MODEL_TRAINING_WEIGHT)
            ),
            new StepDefinition(Algo.DRF.name(),
                    new Step("def_1", 2, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("XRT", 3, DEFAULT_MODEL_TRAINING_WEIGHT)
            ),
            new StepDefinition(Algo.GBM.name(),
                    new Step("def_5", 1, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_2", 2, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_3", 2, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_4", 2, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("def_1", 3, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("grid_1", 4, 2*DEFAULT_GRID_TRAINING_WEIGHT),
                    new Step("lr_annealing", 7, DEFAULT_MODEL_TRAINING_WEIGHT)
            ),
            new StepDefinition(Algo.DeepLearning.name(),
                    new Step("def_1", 3, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("grid_1", 4, DEFAULT_GRID_TRAINING_WEIGHT),
                    new Step("grid_2", 5, DEFAULT_GRID_TRAINING_WEIGHT),
                    new Step("grid_3", 5, DEFAULT_GRID_TRAINING_WEIGHT)
            ),
            new StepDefinition("completion",
                    new Step("resume_best_grids", 6, 2*DEFAULT_GRID_TRAINING_WEIGHT)
            ),
            new StepDefinition(Algo.StackedEnsemble.name(),
                    new Step("monotonic", 9, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("best_of_family_xglm", 10, DEFAULT_MODEL_TRAINING_WEIGHT),
                    new Step("all_xglm", 10, DEFAULT_MODEL_TRAINING_WEIGHT)
            )
  };

    public static StepDefinition[] defaultPlan() {
        return TEN_LAYERED;
    }
    
    private ModelingPlans() {}

}
