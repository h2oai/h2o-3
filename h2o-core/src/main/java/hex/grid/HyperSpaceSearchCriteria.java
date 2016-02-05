package hex.grid;

import water.Iced;

/**
 * Search criteria for a hyperparameter search including directives for how to search and
 * when to stop the search.
 */
public class HyperSpaceSearchCriteria extends Iced {
  public enum Strategy { Unknown, Cartesian, RandomDiscrete } // search strategy

  public final Strategy _strategy;
  public final Strategy strategy() { return _strategy; }

// TODO: add a factory which accepts a Strategy and calls the right constructor

  public HyperSpaceSearchCriteria(Strategy strategy) {
    this._strategy = strategy;
  }

  /**
   * Search criteria for an exhaustive Cartesian hyperparameter search.
   */
  public static final class CartesianSearchCriteria extends HyperSpaceSearchCriteria {
    public CartesianSearchCriteria() {
      super(Strategy.Cartesian);
    }
  }

  /**
   * Search criteria for a hyperparameter search including directives for how to search and
   * when to stop the search.
   */
  public static final class RandomDiscreteValueSearchCriteria extends HyperSpaceSearchCriteria {
    private long _seed = -1;

    // stopping criteria:
    private int _max_models = Integer.MAX_VALUE;
    private int _max_time_ms = Integer.MAX_VALUE;

    public long seed() { return _seed; }
    public int max_models() { return _max_models; }
    public int max_time_ms() { return _max_time_ms; }

    public RandomDiscreteValueSearchCriteria() {
      super(Strategy.RandomDiscrete);
    }
  }
}
