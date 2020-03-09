package hex.grid;

import hex.ScoreKeeper.StoppingMetric;
import water.Iced;
import water.fvec.Frame;

/**
 * Search criteria for a hyperparameter search including directives for how to search and
 * when to stop the search.
 */
public class HyperSpaceSearchCriteria extends Iced {
  public enum Strategy { Unknown, Cartesian, RandomDiscrete, Sequential} // search strategy

  public static class StoppingCriteria extends Iced {

    public static class Builder {
      private StoppingCriteria _criteria = new StoppingCriteria();
      public Builder maxModels(int maxModels) { _criteria._max_models = maxModels; return this; }
      public Builder maxRuntimeSecs(int maxRuntimeSecs) { _criteria._max_runtime_secs = maxRuntimeSecs; return this; }
      public Builder stoppingMetric(StoppingMetric stoppingMetric) { _criteria._stopping_metric = stoppingMetric; return this; }
      public Builder stoppingRounds(int stoppingRounds) { _criteria._stopping_rounds = stoppingRounds; return this; }
      public Builder stoppingTolerance(int stoppingTolerance) { _criteria._stopping_tolerance = stoppingTolerance; return this; }
      public StoppingCriteria build() { return _criteria; }
    }

    public static Builder create() { return new Builder(); }

    // keeping those private fields in snake-case so that they can be set from Schema through reflection
    private int _max_models = 0;  // no limit
    private double _max_runtime_secs = 0;  // no time limit
    private StoppingMetric _stopping_metric = StoppingMetric.AUTO;
    private int _stopping_rounds = 0;
    private double _stopping_tolerance = 1e-3;  // = Model.Parameters.defaultStoppingTolerance()

    public StoppingCriteria() {}

    public int getMaxModels() {
      return _max_models;
    }

    public double getMaxRuntimeSecs() {
      return _max_runtime_secs;
    }

    public int getStoppingRounds() {
      return _stopping_rounds;
    }

    public StoppingMetric getStoppingMetric() {
      return _stopping_metric;
    }

    public double getStoppingTolerance() {
      return _stopping_tolerance;
    }
  }


  public final Strategy _strategy;
  public final Strategy strategy() { return _strategy; }

  public StoppingCriteria stoppingCriteria() { return null; }


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
   * <p>
   * NOTE: client ought to call set_default_stopping_tolerance_for_frame(Frame) to get a reasonable stopping tolerance, especially for small N.
   */
  public static final class RandomDiscreteValueSearchCriteria extends HyperSpaceSearchCriteria {
    private long _seed = -1; // -1 means true random
    private StoppingCriteria _stoppingCriteria;

    public RandomDiscreteValueSearchCriteria() {
      super(Strategy.RandomDiscrete);
      _stoppingCriteria = new StoppingCriteria();
    }

    @Override
    public StoppingCriteria stoppingCriteria() {
      return _stoppingCriteria;
    }

    /** Seed for the random choices of hyperparameter values.  Set to a value other than -1 to get a repeatable pseudorandom sequence. */
    public long seed() { return _seed; }

    /** Max number of models to build. */
    public int max_models() { return _stoppingCriteria._max_models; }

    /**
     * Max runtime for the entire grid, in seconds. Set to 0 to disable. Can be combined with <i>max_runtime_secs</i> in the model parameters. If
     * <i>max_runtime_secs</i> is not set in the model parameters then each model build is launched with a limit equal to
     * the remainder of the grid time.  If <i>max_runtime_secs</i> <b>is</b> set in the mode parameters each build is launched
     * with a limit equal to the minimum of the model time limit and the remaining time for the grid.
     */
    public double max_runtime_secs() { return _stoppingCriteria._max_runtime_secs; }

    /**
     * Early stopping based on convergence of stopping_metric.
     * Stop if simple moving average of the stopping_metric does not improve by stopping_tolerance for
     * k scoring events.
     * Can only trigger after at least 2k scoring events. Use 0 to disable.
     */
    public int stopping_rounds() { return _stoppingCriteria._stopping_rounds; }

    /** Metric to use for convergence checking; only for _stopping_rounds > 0 */
    public StoppingMetric stopping_metric() { return _stoppingCriteria._stopping_metric; }

    /** Relative tolerance for metric-based stopping criterion: stop if relative improvement is not at least this much. */
    public double stopping_tolerance() { return _stoppingCriteria._stopping_tolerance; }

    /** Calculate a reasonable stopping tolerance for the Frame.
     * Currently uses only the NA percentage and nrows, but later
     * can take into account the response distribution, response variance, etc.
     * <p>
     * <pre>1/Math.sqrt(frame.naFraction() * frame.numRows())</pre>
     */
    public static double default_stopping_tolerance_for_frame(Frame frame) {
      return Math.min(0.05, Math.max(0.001, 1/Math.sqrt((1 - frame.naFraction()) * frame.numRows())));
    }

    public void set_default_stopping_tolerance_for_frame(Frame frame) {
      set_stopping_tolerance(default_stopping_tolerance_for_frame(frame));
    }

    public void set_seed(long seed) {
      this._seed = seed;
    }

    public void set_max_models(int max_models) {
      cloneStoppingCriteria()._max_models = max_models;
    }

    public void set_max_runtime_secs(double max_runtime_secs) {
      cloneStoppingCriteria()._max_runtime_secs = max_runtime_secs;
    }

    public void set_stopping_rounds(int stopping_rounds) {
      cloneStoppingCriteria()._stopping_rounds = stopping_rounds;
    }

    public void set_stopping_metric(StoppingMetric stopping_metric) {
      cloneStoppingCriteria()._stopping_metric = stopping_metric;
    }

    public void set_stopping_tolerance(double stopping_tolerance) {
      cloneStoppingCriteria()._stopping_tolerance = stopping_tolerance;
    }

    private StoppingCriteria cloneStoppingCriteria() {
      _stoppingCriteria = (StoppingCriteria) _stoppingCriteria.clone();
      return _stoppingCriteria;
    }
  }

  public static final class SequentialSearchCriteria extends HyperSpaceSearchCriteria {

    private StoppingCriteria _stoppingCriteria;

    public SequentialSearchCriteria() {
      this(new StoppingCriteria());
    }

    public SequentialSearchCriteria(StoppingCriteria stoppingCriteria) {
      super(Strategy.Sequential);
      _stoppingCriteria = stoppingCriteria;
    }

    @Override
    public StoppingCriteria stoppingCriteria() {
      return _stoppingCriteria;
    }
  }

}
