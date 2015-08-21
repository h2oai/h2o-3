package hex.grid;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;

import hex.Model;
import water.Futures;
import water.H2O;
import water.Key;
import water.Lockable;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.IcedLong;
import water.util.PojoUtils;

/**
 * A Grid of Models representing result of hyper-parameter space exploration.  Lazily filled in,
 * this object represents the potentially infinite variety of hyperparameters of a given model &
 * dataset.
 *
 * @param <MP> type of model build parameters
 */
public class Grid<MP extends Model.Parameters>
    extends Lockable<Grid<MP>> {

  /**
   * Publicly available Grid prototype - used by REST API.
   *
   * @see hex.schemas.GridSchemaV99
   */
  public static final Grid GRID_PROTO = new Grid(null, null, null, null);

  /**
   * A cache of double[] hyper-parameters mapping to Models.
   */
  private final IcedHashMap<IcedLong, Key<Model>> _cache = new IcedHashMap<>();

  /**
   * Used "based" model parameters for this grid search.
   */
  private final MP _params;

  /**
   * Failed model parameters - represents points in hyper space for which model generation failed.
   * If the element is null, then look into
   */
  private MP[] _failed_params;

  /**
   * Detailed messages about a failure for given failed model parameters in
   * <code>_failed_params</code>.
   */
  private String[] _failure_details;

  /**
   * Contains "raw" representation of parameters which fail The parameters are represented in
   * textual form, since simple <code>java.lang.Object</code> cannot be serialized by H2O
   * serialization.
   */
  private String[][] _failed_raw_params;

  /**
   * Name of model generated included in this grid.
   */
  private final String _modelName;

  /**
   * Names of used hyper parameters for this grid search.
   */
  private final String[] _hyper_names;

  /**
   * Construct a new grid object to store results of grid search.
   *
   * @param key        reference to this object
   * @param params     initial parameters used by grid search
   * @param hyperNames names of used hyper parameters
   * @param modelName  name of model included in this object (e.g., "GBM")
   */
  protected Grid(Key key, MP params, String[] hyperNames, String modelName) {
    super(key);
    _params = params != null ? (MP) params.clone() : null;
    _hyper_names = hyperNames;
    _modelName = modelName;
    Class<MP> paramsClass = params != null ? (Class<MP>) params.getClass() : null;
    _failed_params = paramsClass != null ? (MP[]) Array.newInstance(paramsClass, 0) : null;
    _failure_details = new String[]{};
    _failed_raw_params = new String[][]{};
  }

  /**
   * Returns name of model included in this object.
   *
   * @return name of model (for example, "DRF", "GBM")
   */
  public String getModelName() {
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
   * Returns the data frame used to train all these models. <p> All models are trained on the same
   * data frame, but might be validated on multiple different frames. </p>
   *
   * @return training frame shared among all models
   */
  public Frame getTrainingFrame() {
    return _params.train();
  }

  /**
   * Returns model for given combination of model parameters or null if the model does not exist.
   *
   * @param params parameters of the model
   * @return A model run with these parameters, or null if the model does not exist.
   */
  public Model getModel(MP params) {
    Key<Model> mKey = getModelKey(params);
    return mKey != null ? mKey.get() : null;
  }

  public Key<Model> getModelKey(MP params) {
    long checksum = params.checksum();
    return getModelKey(checksum);
  }

  Key<Model> getModelKey(long paramsChecksum) {
    Key<Model> mKey = _cache.get(IcedLong.valueOf(paramsChecksum));
    return mKey;
  }

  /* FIXME:  should pass model parameters instead of checksum, but model
   * parameters are not imutable and model builder modifies them! */
  /* package */
  synchronized Key<Model> putModel(long checksum, Key<Model> modelKey) {
    return _cache.put(IcedLong.valueOf(checksum), modelKey);
  }

  /**
   * This method appends a new item to the list of failed model parameters.
   *
   * <p> The failed parameters object represents a point in hyper space which cannot be used for
   * model building. </p>
   *
   * @param params    model parameters which caused model builder failure, can be null
   * @param rawParams array of "raw" parameter values
   * @params failureDetails  textual description of model building failure
   */
  private void appendFailedModelParameters(MP params, String[] rawParams, String failureDetails) {
    assert rawParams != null : "API has to always pass rawParams";
    // Append parameter
    MP[] a = _failed_params;
    MP[] na = Arrays.copyOf(a, a.length + 1);
    na[a.length] = params;
    _failed_params = na;
    // Append message
    String[] m = _failure_details;
    String[] nm = Arrays.copyOf(m, m.length + 1);
    nm[m.length] = failureDetails;
    _failure_details = nm;
    // Append raw parames
    String[][] rp = _failed_raw_params;
    String[][] nrp = Arrays.copyOf(rp, rp.length + 1);
    nrp[rp.length] = rawParams;
    _failed_raw_params = nrp;
  }

  /**
   * This method appends a new item to the list of failed model parameters.
   *
   * <p> The failed parameters object represents a point in hyper space which cannot be used for
   * model building.</p>
   *
   * <p> Should be used only from <code>GridSearch</code> job.</p>
   *
   * @param params model parameters which caused model builder failure
   * @params failureDetails textual description of model building failure
   */
  void appendFailedModelParameters(MP params, String failureDetails) {
    assert params != null : "Model parameters should be always != null !";
    String[] rawParams = ArrayUtils.toString(getHyperValues(params));
    appendFailedModelParameters(params, rawParams, failureDetails);
  }

  /**
   * This method appends a new item to the list of failed hyper-parameters.
   *
   * <p> The failed parameters object represents a point in hyper space which cannot be used to
   * construct a new model parameters.</p>
   *
   * <p> Should be used only from <code>GridSearch</code> job.</p>
   *
   * @param rawParams list of "raw" hyper values which caused a failure to prepare model parameters
   * @params failureDetails textual description of model building failure
   */
  /* package */ void appendFailedModelParameters(Object[] rawParams, String failureDetails) {
    assert rawParams != null : "Raw parameters should be always != null !";
    appendFailedModelParameters(null, ArrayUtils.toString(rawParams), failureDetails);
  }

  /**
   * Returns keys of all models included in this object.
   *
   * @return list of model keys
   */
  public Key<Model>[] getModelKeys() {
    return _cache.values().toArray(new Key[_cache.size()]);
  }

  /**
   * Return all models included in this grid object.
   *
   * @return all models in this grid
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
   * Returns number of models in this grid.
   */
  public int getModelCount() {
    return _cache.size();
  }

  /**
   * Returns number of unsuccessful attempts to build a model.
   */
  public int getFailureCount() {
    return _failed_params.length;
  }

  /**
   * Returns an array of model parameters which caused model build failure.
   *
   * The null-element in the array means, that model parameters cannot be constructed, and the
   * client should use {@link #getFailedParameters()} to obtain "raw" model parameters.
   *
   * Note: cannot return <code>MP[]</code> because of PUBDEV-1863 See:
   * https://0xdata.atlassian.net/browse/PUBDEV-1863
   */
  public Model.Parameters[] getFailedParameters() {
    return _failed_params;
  }

  /**
   * Returns detailed messages about model build failures.
   */
  public String[] getFailureDetails() {
    return _failure_details;
  }

  /**
   * Returns list of raw model parameters causing model building failure.
   */
  public String[][] getFailedRawParameters() {
    return _failed_raw_params;
  }

  /**
   * Return value of hyper parameters used for this grid search.
   *
   * @param parms model parameters
   * @return values of hyper parameters used by grid search producing this grid object.
   */
  public Object[] getHyperValues(MP parms) {
    Object[] result = new Object[_hyper_names.length];
    for (int i = 0; i < _hyper_names.length; i++) {
      result[i] = PojoUtils.getFieldValue(parms, _hyper_names[i]);
    }
    return result;
  }

  /**
   * Returns an array of used hyper parameters names.
   *
   * @return names of hyper parameters used in this hyper search
   */
  public String[] getHyperNames() {
    return _hyper_names;
  }

  // Cleanup models and grid
  @Override
  protected Futures remove_impl(final Futures fs) {
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

