package hex.schemas;

/**
 * Search criteria for a hyperparameter search including directives for how to search and
 * when to stop the search.
 */
public class HyperSpaceSearchCriteriaV99<I, S> extends Schema<I, S> {

  @API(help = "Hyperparameter space search strategy.", required = true, values = { "Unknown", "Cartesian", "RandomDiscrete" }, direction = API.Direction.INOUT)
  public Strategy strategy;

// TODO: add a factory which accepts a Strategy and calls the right constructor

  /**
   * Search criteria for an exhaustive Cartesian hyperparameter search.
   */
  public class CartesianSearchCriteriaV99 extends HyperSpaceSearchCriteriaV99<CartesianSearchCriteria, CartesianSearchCriteriaV99> {
    public CartesianSearchCriteriaV99() {
      strategy = Cartesian;
    }
  }

  /**
   * Search criteria for random hyperparameter search using hyperparameter values given by
   * lists. Includes directives for how to search and when to stop the search.
   */
  public class RandomDiscreteValueSearchCriteriaV99 extends HyperSpaceSearchCriteriaV99<RandomDiscreteValueSearchCriteria, RandomDiscreteValueSearchCriteriaV99> {

    @API(help = "Seed for random number generator; used for reproducibility.", required = false, direction = API.Direction.INOUT)
    public long seed;

    @API(help = "Maximum number of models to build (optional).", required = false, direction = API.Direction.INOUT)
    public int max_models;

    @API(help = "Maximum time to spend building models (optional).", required = false, direction = API.Direction.INOUT)
    public int max_time_ms;
  }
}
