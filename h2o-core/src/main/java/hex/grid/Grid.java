package hex.grid;

import hex.*;
import hex.faulttolerance.Recoverable;
import hex.faulttolerance.Recovery;
import water.*;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;
import water.fvec.persist.PersistUtils;
import water.persist.Persist;
import water.util.*;
import water.util.PojoUtils.FieldNaming;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.*;

import static hex.grid.GridSearch.IGNORED_FIELDS_PARAM_HASH;

/**
 * A Grid of Models representing result of hyper-parameter space exploration.
 * Lazily filled in, this object represents the potentially infinite variety
 * of hyperparameters of a given model & dataset.
 *
 * @param <MP> type of model build parameters
 */
public class Grid<MP extends Model.Parameters> extends Lockable<Grid<MP>> implements ModelContainer<Model>, Recoverable<Grid<MP>> {

  /**
   * Publicly available Grid prototype - used by REST API.
   *
   * @see hex.schemas.GridSchemaV99
   */
  public static final Grid GRID_PROTO = new Grid(null, null, null, new HashMap<>(), null, null, 0);

  // A cache of double[] hyper-parameters mapping to Models.
  private final IcedHashMap<IcedLong, Key<Model>> _models = new IcedHashMap<>();

  private final IcedHashMap<Key<Model>, SearchFailure> _failures;

  // Used "based" model parameters for this grid search.
  private final MP _params;

  // Names of used hyper parameters for this grid search.
  private final String[] _hyper_names;
  private HyperParameters _hyper_params;
  private int _parallelism;
  private HyperSpaceSearchCriteria _search_criteria;

  private final FieldNaming _field_naming_strategy;

  private ScoringInfo[] _scoring_infos = null;


  /**
   * A special key to identify failures of models that did not become part of the {@link Grid}
   */
  private static final Key<Model> NO_MODEL_FAILURES_KEY = Key.makeUserHidden("GridSearchFailureEmptyModelKey");

  /**
   * Failure that occurred during hyperspace exploration.
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
    
    // collect warning
    private String[] _warning_details;

    private SearchFailure(final Class<MP> paramsClass) {
      _failed_params = paramsClass != null ? (MP[]) Array.newInstance(paramsClass, 0) : null;
      _failure_details = new String[]{};
      _failed_raw_params = new String[][]{};
      _failure_stack_traces = new String[]{};
      _warning_details = new String[]{};
    }

    /**
     * This method appends a new item to the list of failed model parameters.
     * <p/>
     * <p> The failed parameters object represents a point in hyper space which cannot be used for
     * model building. </p>
     *
     * @param params    model parameters which caused model builder failure, can be null
     * @param rawParams array of "raw" parameter values
     * @param failureDetails  textual description of model building failure
     * @param stackTrace  stringify stacktrace
     */
    private void appendFailedModelParameters(MP params, String[] rawParams, String failureDetails, String stackTrace) {
      assert rawParams != null : "API has to always pass rawParams";
      _failed_params = ArrayUtils.append(_failed_params, params);
      _failure_details = ArrayUtils.append(_failure_details, failureDetails);
      _failed_raw_params = ArrayUtils.append(_failed_raw_params, new String[][]{rawParams});
      _failure_stack_traces = ArrayUtils.append(_failure_stack_traces, stackTrace);
    }
    
    public void addWarning(String message) {
      Log.warn(message);
      _warning_details = ArrayUtils.append(_warning_details, message);
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
     * @param e exception causing a failure
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
    
    public String[] getWarningDetails() {
      return _warning_details;
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
  protected Grid(
      Key key, MP params, 
      String[] hyperNames,
      Map<String, Object[]> hyperParams,
      HyperSpaceSearchCriteria searchCriteria,
      FieldNaming fieldNaming,
      int parallelism
  ) {
    super(key);
    _params = params != null ? (MP) params.clone() : null;
    _hyper_names = hyperNames;
    _failures = new IcedHashMap<>();
    _field_naming_strategy = fieldNaming;
    update(hyperParams, searchCriteria, parallelism);
  }
  
  protected Grid(Key key, HyperSpaceWalker<MP, ?> walker, int parallelism) {
    this(
        key,
        walker.getParams(),
        walker.getAllHyperParamNames(),
        walker.getHyperParams(),
        walker.search_criteria(),
        walker.getParametersBuilderFactory().getFieldNamingStrategy(),
        parallelism
    );
  }

  public void update(Map<String,Object[]> hyperParams, HyperSpaceSearchCriteria searchCriteria, int parallelism) {
    _hyper_params = new HyperParameters(hyperParams);
    _search_criteria = searchCriteria;
    _parallelism = parallelism;
  }
  
  public Map<String, Object[]> getHyperParams() {
    return _hyper_params.getValues();
  }

  public HyperSpaceSearchCriteria getSearchCriteria() {
    return _search_criteria;
  }

  public int getParallelism() {
    return _parallelism;
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


  /*
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
    long checksum = params.checksum(IGNORED_FIELDS_PARAM_HASH);
    return getModelKey(checksum);
  }

  Key<Model> getModelKey(long paramsChecksum) {
    Key<Model> mKey = _models.get(IcedLong.valueOf(paramsChecksum));
    return mKey;
  }

  /* FIXME: should pass model parameters instead of checksum, but model
   * parameters are not immutable and model builder modifies them! */
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
   * @param t the exception causing a failure
   */
  private void appendFailedModelParameters(final Key<Model> modelKey, final MP params, final String[] rawParams,
                                           final Throwable t) {
      final String failureDetails = isJobCanceled(t) ? "Job Canceled" : t.getMessage();
      final String stackTrace = StringUtils.toString(t);
      final Key<Model> searchedKey = modelKey != null ? modelKey : NO_MODEL_FAILURES_KEY;
      SearchFailure searchFailure = _failures.get(searchedKey);
      if (searchFailure == null) {
        searchFailure = new SearchFailure(_params.getClass());
        _failures.put(searchedKey, searchFailure);
      }
      searchFailure.appendFailedModelParameters(params, rawParams, failureDetails, stackTrace);
      if (params != null) params.addSearchWarnings(searchFailure, this);
  }

  static boolean isJobCanceled(final Throwable t) {
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
   * @param t the exception causing a failure
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
   * @param e the exception causing a failure
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
  @Override
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
  @Override
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
  @Override
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
    if (_params != null) _params.addSearchWarnings(searchFailure, this);

    for (SearchFailure f : values) {
      searchFailure.appendFailedModelParameters(f._failed_params, f._failed_raw_params, f._failure_details,
              f._failure_stack_traces);
    }
    return searchFailure;
  }
  
  public int countTotalFailures() {
    return _failures.values().stream().mapToInt(SearchFailure::getFailureCount).sum();
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
      result[i] = parms.getParameter(_field_naming_strategy.toDest(_hyper_names[i]));
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
    String[] colFormats = new String[_hyper_names.length + extra_len];

    // Set the default type to string
    Arrays.fill(colTypes, "string");
    Arrays.fill(colFormats, "%s");

    // Change where appropriate (and only the hyper params)
    for (int i = 0; i < _hyper_names.length; i++) {
      Object[] objects = _hyper_params.getValues().get(_hyper_names[i]);
      if (objects != null && objects.length > 0) {
        Object obj = objects[0];
        if (obj instanceof Double || obj instanceof Float) {
          colTypes[i] = "double";
          colFormats[i] = "%.5f";
        } else if (obj instanceof Integer || obj instanceof Long) {
          colTypes[i] = "long";
          colFormats[i] = "%d";
        }
      }
    }
    if (sort_by != null) {
      colTypes[colTypes.length-1] = "double";
      colFormats[colFormats.length-1] = "%.5f";
    }

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
      for (j = 0; j < _hyper_names.length; ++j) {
        Object paramValue = parms.getParameter(_field_naming_strategy.toDest(_hyper_names[j]));
        if (paramValue.getClass().isArray()) {
          // E.g., GLM alpha/lambda parameters can be arrays with one value
          if (paramValue instanceof float[] && ((float[])paramValue).length == 1) paramValue = ((float[]) paramValue)[0];
          else if (paramValue instanceof double[] && ((double[])paramValue).length == 1) paramValue = ((double[]) paramValue)[0];
          else if (paramValue instanceof int[] && ((int[])paramValue).length == 1) paramValue = ((int[]) paramValue)[0];
          else if (paramValue instanceof long[] && ((long[])paramValue).length == 1) paramValue = ((long[]) paramValue)[0];
          else if (paramValue instanceof Object[] && ((Object[])paramValue).length == 1) paramValue = ((Object[]) paramValue)[0];
        }
        table.set(i, j, paramValue);
      }
      table.set(i, j, km.toString());
      if (sort_by != null) table.set(i, j + 1, ModelMetrics.getMetricFromModel(km, sort_by));
      i++;
    }
    Log.info(table);
    return table;
  }

  public TwoDimTable createScoringHistoryTable() {
    if (0 == _models.values().size()) {
      return ScoringInfo.createScoringHistoryTable(_scoring_infos, false, false, ModelCategory.Binomial, false, false);
    }

    Key<Model> k = null;

    for (Key<Model> foo : _models.values()) {
      k = foo;
      break;
    }

    Model m = k.get();

    if (null == m) {
      Log.warn("Cannot create grid scoring history table; Model has been removed: " + k);
      return ScoringInfo.createScoringHistoryTable(_scoring_infos, false, false, ModelCategory.Binomial, false, false);
    }

    ScoringInfo scoring_info = _scoring_infos != null && _scoring_infos.length > 0 ? _scoring_infos[0] : null;
    return ScoringInfo.createScoringHistoryTable(_scoring_infos, (scoring_info != null ? scoring_info.validation : false), (scoring_info != null ? scoring_info.cross_validation: false), m._output.getModelCategory(), (scoring_info != null ? scoring_info.is_autoencoder : false), m._parms.hasCustomMetricFunc());
  }

  /**
   * Exports this Grid in a binary format using {@link AutoBuffer}. Related models are not saved.
   *
   * @param gridExportDir Full path to the folder this {@link Grid} should be saved to
   * @return Path of the file written
   */
  public List<String> exportBinary(final String gridExportDir, final boolean exportModels, ModelExportOption... options) {
    Objects.requireNonNull(gridExportDir);
    assert _key != null;
    final String gridFilePath = gridExportDir + "/" + _key;
    final URI gridUri = FileUtils.getURI(gridFilePath);
    PersistUtils.write(gridUri, (ab) -> ab.put(this));
    List<String> result = new ArrayList<>();
    result.add(gridFilePath);
    if (exportModels) {
      exportModelsBinary(result, gridExportDir, options);
    }
    return result;
  }

  private void exportModelsBinary(final List<String> files, final String exportDir, ModelExportOption... options) {
    Objects.requireNonNull(exportDir);
    for (Model model : getModels()) {
      try {
        String modelFile = exportDir + "/" + model._key.toString();
        files.add(modelFile);
        model.exportBinaryModel(modelFile, true, options);
      } catch (IOException e) {
        throw new RuntimeException("Failed to write grid model " + model._key.toString(), e);
      }
    }
  }
  
  public static Grid importBinary(final String gridPath, final boolean loadReferences) {
    final URI gridUri = FileUtils.getURI(gridPath);
    if (!PersistUtils.exists(gridUri)) {
      throw new IllegalArgumentException("Grid file not found " + gridUri);
    }
    final Persist persist = H2O.getPM().getPersistForURI(gridUri);
    final String gridDirectory = persist.getParent(gridUri.toString());
    final Grid grid = readGridBinary(gridUri, persist);
    final Recovery<Grid> recovery = new Recovery<>(gridDirectory);
    URI gridReferencesUri = FileUtils.getURI(recovery.referencesMetaFile(grid));
    if (loadReferences && !PersistUtils.exists(gridReferencesUri)) {
      throw new IllegalArgumentException("Requested to load with references, but the grid was saved without references.");
    }
    grid.importModelsBinary(gridDirectory);
    if (loadReferences) {
      recovery.loadReferences(grid);
    }
    DKV.put(grid);
    return grid;
  }
  
  private static Grid readGridBinary(final URI gridUri, Persist persist) {
    try (final InputStream inputStream = persist.open(gridUri.toString())) {
      final AutoBuffer gridAutoBuffer = new AutoBuffer(inputStream);
      final Freezable freezable = gridAutoBuffer.get();
      if (!(freezable instanceof Grid)) {
        throw new IllegalArgumentException(String.format("Given file '%s' is not a Grid", gridUri.toString()));
      }
      return (Grid) freezable;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to open grid file.", e);
    }
  }

  private void importModelsBinary(final String exportDir) {
    for (Key<Model> k : _models.values()) {
      String modelFile = exportDir + "/" + k.toString();
      try {
        final Model<?, ?, ?> model = Model.importBinaryModel(modelFile);
        assert model != null;
      } catch (IOException e) {
        throw new IllegalStateException("Unable to load model from " + modelFile, e);
      }
    }
  }

  @Override
  public Set<Key<?>> getDependentKeys() {
    return _params.getDependentKeys();
  }

  public MP getParams() {
    return _params;
  }

}
