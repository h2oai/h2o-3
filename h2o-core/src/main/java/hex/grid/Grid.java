package hex.grid;

import hex.*;
import water.*;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OConcurrentModificationException;
import water.fvec.Frame;
import water.persist.Persist;
import water.util.*;
import water.util.PojoUtils.FieldNaming;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * A Grid of Models representing result of hyper-parameter space exploration.
 * Lazily filled in, this object represents the potentially infinite variety
 * of hyperparameters of a given model & dataset.
 *
 * @param <MP> type of model build parameters
 */
public class Grid<MP extends Model.Parameters> extends Lockable<Grid<MP>> implements ModelContainer<Model> {

  /**
   * Publicly available Grid prototype - used by REST API.
   *
   * @see hex.schemas.GridSchemaV99
   */
  public static final Grid GRID_PROTO = new Grid(null, null, null, null);

  // A cache of double[] hyper-parameters mapping to Models.
  private final IcedHashMap<IcedLong, Key<Model>> _models = new IcedHashMap<>();

  private final IcedHashMap<Key<Model>, SearchFailure> _failures;

  // Used "based" model parameters for this grid search.
  private final MP _params;

  // Names of used hyper parameters for this grid search.
  private final String[] _hyper_names;

  private final FieldNaming _field_naming_strategy;

  private ScoringInfo[] _scoring_infos = null;


  /**
   * A special key to identify failures of models that did not become part of the {@link Grid}
   */
  private static final Key<Model> NO_MODEL_FAILURES_KEY = Key.makeUserHidden("GridSearchFailureEmptyModelKey");

  /**
   * A failure occured during hyperspace exploration.
   */
  public static final class SearchFailure<MP extends Model.Parameters> extends Iced<SearchFailure> {

    // Failed model parameters - represents points in hyper space for which model
    // generation failed.  If the element is null, then look into
    private MP[] _failed_params;

    // Detailed messages about a failure for given failed model parameters in
    // <code>_failed_params</code>.
    private String[] _failure_details;

    // Collected stack trace for failure.
    private String[] _failure_stack_traces;

    // Contains "raw" representation of parameters which fail The parameters are
    // represented in textual form, since simple <code>java.lang.Object</code>
    // cannot be serialized by H2O serialization.
    private String[][] _failed_raw_params;

    private SearchFailure(final Class<MP> paramsClass) {
      _failed_params = paramsClass != null ? (MP[]) Array.newInstance(paramsClass, 0) : null;
      _failure_details = new String[]{};
      _failed_raw_params = new String[][]{};
      _failure_stack_traces = new String[]{};
    }

    /**
     * This method appends a new item to the list of failed model parameters.
     * <p/>
     * <p> The failed parameters object represents a point in hyper space which cannot be used for
     * model building. </p>
     *
     * @param params    model parameters which caused model builder failure, can be null
     * @param rawParams array of "raw" parameter values
     * @params failureDetails  textual description of model building failure
     * @params stackTrace  stringify stacktrace
     */
    private void appendFailedModelParameters(MP params, String[] rawParams, String failureDetails, String stackTrace) {
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
      // Append raw params
      String[][] rp = _failed_raw_params;
      String[][] nrp = Arrays.copyOf(rp, rp.length + 1);
      nrp[rp.length] = rawParams;
      _failed_raw_params = nrp;
      // Append stack trace
      String[] st = _failure_stack_traces;
      String[] nst = Arrays.copyOf(st, st.length + 1);
      nst[st.length] = stackTrace;
      _failure_stack_traces = nst;
    }

    public void appendFailedModelParameters(final MP[] params, final String[][] rawParams,
                                            final String[] failureDetails, final String[] stackTraces) {

      assert rawParams != null : "API has to always pass rawParams";

      _failed_params = ArrayUtils.append(_failed_params, params);
      _failed_raw_params = ArrayUtils.append(_failed_raw_params, rawParams);
      _failure_details = ArrayUtils.append(_failure_details, failureDetails);
      _failure_stack_traces = ArrayUtils.append(_failure_stack_traces, stackTraces);
    }

    /**
     * This method appends a new item to the list of failed hyper-parameters.
     * <p/>
     * <p> The failed parameters object represents a point in hyper space which cannot be used to
     * construct a new model parameters.</p>
     * <p/>
     * <p> Should be used only from <code>GridSearch</code> job.</p>
     *
     * @param rawParams list of "raw" hyper values which caused a failure to prepare model parameters
     * @params e exception causing a failure
     */
    /* package */ void appendFailedModelParameters(Object[] rawParams, Exception e) {
      assert rawParams != null : "Raw parameters should be always != null !";
      appendFailedModelParameters(null, ArrayUtils.toString(rawParams), e.getMessage(), StringUtils.toString(e));
    }

    public Model.Parameters[] getFailedParameters() {
      return _failed_params;
    }

    public String[] getFailureDetails() {
      return _failure_details;
    }

    public String[] getFailureStackTraces() {
      return _failure_stack_traces;
    }

    public String[][] getFailedRawParameters() {
      return _failed_raw_params;
    }

    public int getFailureCount() {
      return _failed_params.length;
    }
  }

  /**
   * Construct a new grid object to store results of grid search.
   *
   * @param key        reference to this object
   * @param params     initial parameters used by grid search
   * @param hyperNames names of used hyper parameters
   */
  protected Grid(Key key, MP params, String[] hyperNames, FieldNaming fieldNaming) {
    super(key);
    _params = params != null ? (MP) params.clone() : null;
    _hyper_names = hyperNames;
      _field_naming_strategy = fieldNaming;
      _failures = new IcedHashMap<>();
  }

  /**
   * Returns name of model included in this object.  Note: only sensible for
   * Grids which search over a single class of Models.
   *
   * @return name of model (for example, "DRF", "GBM")
   */
  public String getModelName() {
    return _params.algoName();
  }

  public ScoringInfo[] getScoringInfos() {
    return _scoring_infos;
  }

  public void setScoringInfos(ScoringInfo[] scoring_infos) {
    this._scoring_infos = scoring_infos;
  }


  /**
   * Ask the Grid for a suggested next hyperparameter value, given an existing Model as a starting
   * point and the complete set of hyperparameter limits. Returning a NaN signals there is no next
   * suggestion, which is reasonable if the obvious "next" value does not exist (e.g. exhausted all
   * possibilities of an categorical).  It is OK if a Model for the suggested value already exists; this
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
    Key<Model> mKey = _models.get(IcedLong.valueOf(paramsChecksum));
    return mKey;
  }

  /* FIXME:  should pass model parameters instead of checksum, but model
   * parameters are not imutable and model builder modifies them! */
  /* package */
  synchronized Key<Model> putModel(long checksum, Key<Model> modelKey) {
    return _models.put(IcedLong.valueOf(checksum), modelKey);
  }

  /**
   * This method appends a new item to the list of failed model parameters.
   * <p/>
   * <p> The failed parameters object represents a point in hyper space which cannot be used for
   * model building. </p>
   *
   * @param modelKey Model the failures are related to
   * @param params    model parameters which caused model builder failure, can be null
   * @param rawParams array of "raw" parameter values
   * @params failureDetails  textual description of model building failure
   * @params stackTrace  stringify stacktrace
   */
  private void appendFailedModelParameters(final Key<Model> modelKey, final MP params, final String[] rawParams,
                                           final Throwable t) {
      final String failureDetails = isJobCanceled(t) ? "Job Canceled" : t.getMessage();
      final String stackTrace = StringUtils.toString(t);
      final Key<Model> searchedKey = modelKey != null ? modelKey : NO_MODEL_FAILURES_KEY;
      SearchFailure searchFailure = _failures.get(searchedKey);
      if ((searchFailure == null)) {
        searchFailure = new SearchFailure(_params.getClass());
          _failures.put(searchedKey, searchFailure);
      }
      searchFailure.appendFailedModelParameters(params, rawParams, failureDetails, stackTrace);
  }

  private static boolean isJobCanceled(final Throwable t) {
    for (Throwable ex = t; ex != null; ex = ex.getCause()) {
      if (ex instanceof Job.JobCancelledException) {
        return true;
      }
    }
    return false;
  }

  /**
   * This method appends a new item to the list of failed model parameters.
   * <p/>
   * <p> The failed parameters object represents a point in hyper space which cannot be used for
   * model building.</p>
   * <p/>
   * <p> Should be used only from <code>GridSearch</code> job.</p>
   *
   * @param params model parameters which caused model builder failure
   * @params e  exception causing a failure
   */
  void appendFailedModelParameters(final Key<Model> modelKey, final MP params, final Throwable t) {
    assert params != null : "Model parameters should be always != null !";
    String[] rawParams = ArrayUtils.toString(getHyperValues(params));
    appendFailedModelParameters(modelKey, params, rawParams, t);
  }

  /**
   * This method appends a new item to the list of failed hyper-parameters.
   * <p/>
   * <p> The failed parameters object represents a point in hyper space which cannot be used to
   * construct a new model parameters.</p>
   * <p/>
   * <p> Should be used only from <code>GridSearch</code> job.</p>
   *
   * @param rawParams list of "raw" hyper values which caused a failure to prepare model parameters
   * @params e exception causing a failure
   */
  void appendFailedModelParameters(final Key<Model> modelKey, final Object[] rawParams, final Exception e) {
    assert rawParams != null : "Raw parameters should be always != null !";
    appendFailedModelParameters(modelKey, null, ArrayUtils.toString(rawParams), e);
  }

  /**
   * Returns keys of all models included in this object.
   *
   * @return list of model keys sorted lexically
   */
  public Key<Model>[] getModelKeys() {
    Key<Model>[] keys = _models.values().toArray(new Key[_models.size()]);
    Arrays.sort(keys);
    return keys;
  }

  /**
   * Return all models included in this grid object.
   *
   * @return all models in this grid
   */
  public Model[] getModels() {
    Collection<Key<Model>> modelKeys = _models.values();
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
    return _models.size();
  }

  /**
   * Returns all failures currently listed in this Grid instance, including failures related to models not present in
   * the grid that failed during the last run.
   *
   * @return An instance of {@link SearchFailure} with all failures currently linked to this {@link Grid}.
   * An empty {@link SearchFailure} instance is returned if there are no failures listed.
   */
  public SearchFailure getFailures() {
    final Collection<SearchFailure> values = _failures.values();
    // Original failures should be left intact. Also avoid mutability from outer space.
    final SearchFailure searchFailure = new SearchFailure(_params != null ? _params.getClass() : null);

    for (SearchFailure f : values) {
      searchFailure.appendFailedModelParameters(f._failed_params, f._failed_raw_params, f._failure_details,
              f._failure_stack_traces);
    }

    return searchFailure;
  }

  /**
   * Removes failures found while walking the hyperspace related to models not present in Grid.
   */
  protected void clearNonRelatedFailures(){
    _failures.remove(NO_MODEL_FAILURES_KEY);
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
      result[i] = PojoUtils.getFieldValue(parms, _hyper_names[i], _field_naming_strategy);
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
  protected Futures remove_impl(final Futures fs, boolean cascade) {
    if (cascade) {
      for (Key<Model> k : _models.values())
        Keyed.remove(k, fs, true);
    }
    _models.clear();
    return super.remove_impl(fs, cascade);
  }

  /**
   * Write out K/V pairs
   */
  @Override
  protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    for (Key<Model> k : _models.values())
      ab.putKey(k);
    return super.writeAll_impl(ab);
  }

  /**
   * By default, the method writeAll_impl saves all the Grid models as well into the binary file. For some use-cases,
   * this is undesired (Grid checkpointing).
   *
   * @param autoBuffer AutoBuffer to serialize this instance of {@link Grid} to
   * @return Reference to the instance of {@link AutoBuffer} given as a method argument.
   */
  protected AutoBuffer writeWithoutModels(final AutoBuffer autoBuffer){
    return super.writeAll_impl(autoBuffer.put(this));
  }

  @Override
  protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    throw H2O.unimpl();
  }

  @Override
  protected long checksum_impl() {
    throw H2O.unimpl();
  }

  @Override
  public Class<KeyV3.GridKeyV3> makeSchema() {
    return KeyV3.GridKeyV3.class;
  }

  public TwoDimTable createSummaryTable(Key<Model>[] model_ids, String sort_by, boolean decreasing) {
    if (_hyper_names == null || model_ids == null || model_ids.length == 0) return null;
    int extra_len = sort_by != null ? 2 : 1;
    String[] colTypes = new String[_hyper_names.length + extra_len];
    Arrays.fill(colTypes, "string");
    String[] colFormats = new String[_hyper_names.length + extra_len];
    Arrays.fill(colFormats, "%s");
    String[] colNames = Arrays.copyOf(_hyper_names, _hyper_names.length + extra_len);
    colNames[_hyper_names.length] = "model_ids";
    if (sort_by != null)
      colNames[_hyper_names.length + 1] = sort_by;
    TwoDimTable table = new TwoDimTable("Hyper-Parameter Search Summary",
            sort_by != null ? "ordered by " + (decreasing ? "decreasing " : "increasing ") + sort_by : null,
            new String[_models.size()], colNames, colTypes, colFormats, "");
    int i = 0;
    for (Key<Model> km : model_ids) {
      Model m = DKV.getGet(km);
      Model.Parameters parms = m._parms;
      int j;
      for (j = 0; j < _hyper_names.length; ++j)
        table.set(i, j, PojoUtils.getFieldValue(parms, _hyper_names[j], _field_naming_strategy));
      table.set(i, j, km.toString());
      if (sort_by != null) table.set(i, j + 1, ModelMetrics.getMetricFromModel(km, sort_by));
      i++;
    }
    Log.info(table);
    return table;
  }

  public TwoDimTable createScoringHistoryTable() {
    if (0 == _models.values().size()) {
      return ScoringInfo.createScoringHistoryTable(_scoring_infos, false, false, ModelCategory.Binomial, false);
    }

    Key<Model> k = null;

    for (Key<Model> foo : _models.values()) {
      k = foo;
      break;
    }

    Model m = k.get();

    if (null == m) {
      Log.warn("Cannot create grid scoring history table; Model has been removed: " + k);
      return ScoringInfo.createScoringHistoryTable(_scoring_infos, false, false, ModelCategory.Binomial, false);
    }

    ScoringInfo scoring_info = _scoring_infos != null && _scoring_infos.length > 0 ? _scoring_infos[0] : null;
    return ScoringInfo.createScoringHistoryTable(_scoring_infos, (scoring_info != null ? scoring_info.validation : false), (scoring_info != null ? scoring_info.cross_validation: false), m._output.getModelCategory(), (scoring_info != null ? scoring_info.is_autoencoder : false));
  }

  /**
   * Exports this Grid in a binary format using {@link AutoBuffer}. Related models are not saved.
   *
   * @param gridExportDir Full path to the folder this {@link Grid} should be saved to
   * @throws IOException Error serializing the grid.
   */
  public void exportBinary(final String gridExportDir) throws IOException {
    Objects.requireNonNull(gridExportDir);
    final String gridFilePath = gridExportDir + "/" + _key.toString();
    assert _key != null;
    final URI gridUri = FileUtils.getURI(gridFilePath);
    final Persist persist = H2O.getPM().getPersistForURI(gridUri);
    try (final OutputStream outputStream = persist.create(gridUri.toString(), true)) {
      final AutoBuffer autoBuffer = new AutoBuffer(outputStream, true);
      writeWithoutModels(autoBuffer);
      autoBuffer.close();
    }
  }

  /**
   * Saves all of the models present in this Grid. Models are named by their keys.
   *
   * @param exportDir Directory to export all the models to.
   * @throws IOException Error exporting the models
   */
  public void exportModelsBinary(final String exportDir) throws IOException {
    Objects.requireNonNull(exportDir);
    for (Model model : getModels()) {
      model.exportBinaryModel(exportDir + "/" + model._key.toString(), true);
    }
  }

  public MP getParams() {
    return _params;
  }
}
