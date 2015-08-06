package hex.grid;

import java.util.Collection;

import hex.Model;
import water.Futures;
import water.H2O;
import water.Key;
import water.Lockable;
import water.fvec.Frame;
import water.util.IcedHashMap;
import water.util.IcedLong;
import water.util.PojoUtils;

/**
 * A Grid of Models representing result of hyper-parameter space exploration.  Lazily filled in,
 * this object
 * represents the potentially infinite variety of hyperparameters of a given model & dataset.
 *
 * @param <MP> type of model build parameters
 */
// FIXME Should be failed models represented in grid?
public class Grid<MP extends Model.Parameters>
    extends Lockable<Grid<MP>> {

  /**
   * The training frame for this grid of models.
   */
  protected final Frame _fr;

  /**
   * A cache of double[] hyper-parameters mapping to Models.
   */
  public final IcedHashMap<IcedLong, Key<Model>> _cache = new IcedHashMap<>();

  /**
   * Used "based" model parameters for this grid search.
   */
  public final MP _params;

  /**
   * Name of model generated included in this grid.
   */
  public final String _modelName;

  /**
   * Names of used hyper parameters for this grid search.
   */
  final String[] _hyper_names;

  /**
   * Construct a new grid object to store results of grid search.
   *
   * @param key  reference to this object
   * @param params  initial parameters used by grid search
   * @param hyperNames  names of used hyper parameters
   * @param modelName  name of model included in this object (e.g., "GBM")
   */
  public Grid(Key key, MP params, String[] hyperNames, String modelName) {
    super(key);
    // FIXME really i want to save frame?
    _fr = params != null ? params.train() : null;
    _params = params != null ? (MP) params.clone() : null;
    _hyper_names = hyperNames;
    _modelName = modelName;
  }

  /** Returns name of model included in this object.
   *
   * @return name of model (for example, "DRF", "GBM")
   */
  public String modelName() {
    return _modelName;
  }

  /**
   * Ask the Grid for a suggested next hyperparameter value, given an existing Model as a starting
   * point and the complete set of hyperparameter limits. Returning a NaN signals there is no next
   * suggestion, which is reasonable if the obvious "next" value does not exist (e.g. exhausted all
   * possibilities of an enum).  It is OK if a Model for the suggested value already exists; this
   * will be checked before building any model.
   *
   * @param h           The h-th hyperparameter
   * @param m           A model to act as a starting point
   * @param hyperLimits Upper bounds for this search
   * @return Suggested next value for hyperparameter h or NaN if no next value

  protected double suggestedNextHyperValue(int h, Model m, double[] hyperLimits) {
    throw H2O.fail();
  }*/

  /**
   * Returns the data frame used to train all these models.
   * <p>
   *     All models are trained on the same data
   *     frame, but might be validated on multiple different frames.
   * </p>
   * @return training frame shared among all models
   */
  public Frame trainingFrame() {
    return _fr;
  }

  /**
   * Returns model for given combination of model parameters or null if the model does not exist.
   *
   * @param params  parameters of the model
   * @return A model run with these parameters, or null if the model does not exist.
   */
  public Model getModel(MP params) {
    Key<Model> mKey = getModelKey(params);
    return mKey != null ? mKey.get() : null;
  }

  public Key<Model> getModelKey(MP params) {
    long checksum = params.checksum();
    Key<Model> mKey = _cache.get(IcedLong.valueOf(checksum));
    return mKey;
  }

  /* FIXME:  should pass model parameters instead of checksum, but model
   * parameters are not imutable and model builder modifies them! */
  Key<Model> putModel(long checksum, Key<Model> modelKey) {
    return _cache.put(IcedLong.valueOf(checksum), modelKey);
  }

  /**
   * Returns keys of all models included in this object.
   * @return  list of model keys
   */
  public Key<Model>[] getModelKeys() {
    return _cache.values().toArray(new Key[_cache.size()]);
  }

  /**
   * Return all models included in this grid object.
   * @return  all models in this grid
   */
  public Model[] getModels() {
    Collection<Key<Model>> modelKeys = _cache.values();
    Model[] models = new Model[modelKeys.size()];
    int i = 0;
    for (Key<Model> mKey : modelKeys) {
      models[i] = mKey != null ? mKey.get() : null;
      i++;
    }
    return models;
  }

  /**
   * Return value of hyper parameters used for this grid search.
   *
   * @param parms  model parameters
   * @return  values of hyper parameters used by grid search producing this grid object.
   */
  public Object[] getHyperValues(MP parms) {
    Object[] result = new Object[_hyper_names.length];
    for (int i = 0; i < _hyper_names.length; i++) {
      result[i] = PojoUtils.getFieldValue(parms, _hyper_names[i]);
    }
    return result;
  }

  /** Returns an array of used hyper parameters names.
   *
   * @return  names of hyper parameters used in this hyper search
   */
  public String[] getHyperNames() {
    return _hyper_names;
  }

  // Cleanup models and grid
  @Override
  protected Futures remove_impl(Futures fs) {
    for (Key<Model> k : _cache.values()) {
      k.remove(fs);
    }
    _cache.clear();
    return fs;
  }

  @Override
  protected long checksum_impl() {
    throw H2O.unimpl();
  }
}

