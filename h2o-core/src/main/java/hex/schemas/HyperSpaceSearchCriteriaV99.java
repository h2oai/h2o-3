package hex.schemas;

import hex.ScoreKeeper;
import hex.grid.HyperSpaceSearchCriteria;
import water.api.API;
import water.api.SchemaV3;
import water.exceptions.H2OIllegalArgumentException;

/**
 * Search criteria for a hyperparameter search including directives for how to search and
 * when to stop the search.
 */
public class HyperSpaceSearchCriteriaV99<I, S>
    extends SchemaV3<HyperSpaceSearchCriteria, HyperSpaceSearchCriteriaV99.CartesianSearchCriteriaV99> {

  @API(help = "Hyperparameter space search strategy.", required = true, values = { "Unknown", "Cartesian", "RandomDiscrete" }, direction = API.Direction.INOUT)
  public HyperSpaceSearchCriteria.Strategy strategy;

// TODO: add a factory which accepts a Strategy and calls the right constructor

  /**
   * Search criteria for an exhaustive Cartesian hyperparameter search.
   */
  public static class CartesianSearchCriteriaV99 extends HyperSpaceSearchCriteriaV99<HyperSpaceSearchCriteria.CartesianSearchCriteria, CartesianSearchCriteriaV99> {
    public CartesianSearchCriteriaV99() {
      strategy = HyperSpaceSearchCriteria.Strategy.Cartesian;
    }
  }

  /**
   * Search criteria for random hyperparameter search using hyperparameter values given by
   * lists. Includes directives for how to search and when to stop the search.
   */
  public static class RandomDiscreteValueSearchCriteriaV99 extends HyperSpaceSearchCriteriaV99<HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria, RandomDiscreteValueSearchCriteriaV99> {
    public RandomDiscreteValueSearchCriteriaV99() {
      strategy = HyperSpaceSearchCriteria.Strategy.RandomDiscrete;
    }

    public RandomDiscreteValueSearchCriteriaV99(long seed, int max_models, int max_runtime_secs) {
      strategy = HyperSpaceSearchCriteria.Strategy.RandomDiscrete;
      this.seed = seed;
      this.max_models = max_models;
      this.max_runtime_secs = max_runtime_secs;
    }

    @API(help = "Seed for random number generator; set to a value other than -1 for reproducibility.", required = false, direction = API.Direction.INOUT)
    public long seed;

    @API(help = "Maximum number of models to build (optional).", required = false, direction = API.Direction.INOUT)
    public int max_models;

    @API(help = "Maximum time to spend building models (optional).", required = false, direction = API.Direction.INOUT)
    public double max_runtime_secs;

    @API(help = "Early stopping based on convergence of stopping_metric. Stop if simple moving average of length k of the stopping_metric does not improve for k:=stopping_rounds scoring events (0 to disable)", level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true)
    public int stopping_rounds;

    @API(help = "Metric to use for early stopping (AUTO: logloss for classification, deviance for regression)", values = {"AUTO", "deviance", "logloss", "MSE", "AUC", "lift_top_group", "r2", "misclassification"}, level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true)
    public ScoreKeeper.StoppingMetric stopping_metric;

    @API(help = "Relative tolerance for metric-based stopping criterion Relative tolerance for metric-based stopping criterion (stop if relative improvement is not at least this much)", level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true)
    public double stopping_tolerance;
  }

  /**
   * Fill with the default values from the corresponding Iced object.
   */
  public S fillWithDefaults() {
    HyperSpaceSearchCriteria defaults = null;

    if (HyperSpaceSearchCriteria.Strategy.Cartesian == strategy) {
      defaults = new HyperSpaceSearchCriteria.CartesianSearchCriteria();
    } else if (HyperSpaceSearchCriteria.Strategy.RandomDiscrete == strategy) {
      defaults = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
    } else {
      throw new H2OIllegalArgumentException("search_criteria.strategy", strategy.toString());
    }

    fillFromImpl(defaults);

    return (S) this;
  }

}
