package hex.schemas;

import hex.grid.HyperSpaceSearchCriteria;
import water.api.API;
import water.api.Schema;
import water.exceptions.H2OIllegalArgumentException;

/**
 * Search criteria for a hyperparameter search including directives for how to search and
 * when to stop the search.
 */
public class HyperSpaceSearchCriteriaV99<I, S> extends Schema<HyperSpaceSearchCriteria, HyperSpaceSearchCriteriaV99.CartesianSearchCriteriaV99> {

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

    @API(help = "Seed for random number generator; used for reproducibility.", required = false, direction = API.Direction.INOUT)
    public long seed;

    @API(help = "Maximum number of models to build (optional).", required = false, direction = API.Direction.INOUT)
    public int max_models;

    @API(help = "Maximum time to spend building models (optional).", required = false, direction = API.Direction.INOUT)
    public double max_runtime_secs;
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
