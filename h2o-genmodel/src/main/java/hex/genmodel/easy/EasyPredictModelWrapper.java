package hex.genmodel.easy;

import hex.ModelCategory;
import hex.genmodel.*;
import hex.genmodel.algos.deeplearning.DeeplearningMojoModel;
import hex.genmodel.algos.glrm.GlrmMojoModel;
import hex.genmodel.algos.targetencoder.TargetEncoderMojoModel;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.algos.tree.TreeBackedMojoModel;
import hex.genmodel.algos.word2vec.WordEmbeddingModel;
import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.attributes.VariableImportances;
import hex.genmodel.attributes.parameters.IVariableImportancesHolder;
import hex.genmodel.attributes.parameters.KeyValue;
import hex.genmodel.easy.error.VoidErrorConsumer;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import static hex.genmodel.utils.ArrayUtils.nanArray;

/**
 * An easy-to-use prediction wrapper for generated models.  Instantiate as follows.  The following two are equivalent.
 *
 *     EasyPredictModelWrapper model = new EasyPredictModelWrapper(rawModel);
 *
 *     EasyPredictModelWrapper model = new EasyPredictModelWrapper(
 *                                         new EasyPredictModelWrapper.Config()
 *                                             .setModel(rawModel)
 *                                             .setConvertUnknownCategoricalLevelsToNa(false));
 *
 * Note that for any given model, you must use the exact one correct predict method below based on the
 * model category.
 *
 * By default, unknown categorical levels result in a thrown PredictUnknownCategoricalLevelException.
 * The API was designed with this default to make the simplest possible setup inform the user if there are concerns
 * with the data quality.
 * An alternate behavior is to automatically convert unknown categorical levels to N/A.  To do this, use
 * setConvertUnknownCategoricalLevelsToNa(true) instead.
 *
 * Detection of unknown categoricals may be observed by registering an implementation of {@link ErrorConsumer}
 * in the process of {@link Config} creation.
 * 
 * Advanced scoring features are disabled by default for performance reasons. Configuration flags
 * allow the user to output also
 *  - leaf node assignment,
 *  - GLRM reconstructed matrix,
 *  - staged probabilities,
 *  - prediction contributions (SHAP values).
 *
 * Deprecation note: Total number of unknown categorical variables is newly accessible by registering {@link hex.genmodel.easy.error.CountingErrorConsumer}.
 *
 *
 * <p></p>
 * See the top-of-tree master version of this file <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-genmodel/src/main/java/hex/genmodel/easy/EasyPredictModelWrapper.java" target="_blank">here on github</a>.
 */
public class EasyPredictModelWrapper implements Serializable {
  // These private members are read-only after the constructor.
  public final GenModel m;
  private final RowToRawDataConverter rowDataConverter;

  private final boolean useExtendedOutput;
  private final boolean enableLeafAssignment;
  private final boolean enableGLRMReconstruct;  // if set true, will return the GLRM resconstructed value, A_hat=X*Y instead of just X
  private final boolean enableStagedProbabilities; // if set true, staged probabilities from tree agos are returned
  private final boolean enableContributions; // if set to true, will return prediction contributions (SHAP values) - for GBM & XGBoost
  private final int glrmIterNumber; // allow user to set GLRM mojo iteration number in constructing x.

  private final PredictContributions predictContributions;
  
  /**
   * Observer interface with methods corresponding to errors during the prediction.
   */
  public static abstract class ErrorConsumer implements Serializable {
    /**
     * Observe transformation error for data from the predicted dataset.
     *
     * @param columnName Name of the column for which the error is raised
     * @param value      Original value that could not be transformed properly
     * @param message    Transformation error message
     */
    public abstract void dataTransformError(String columnName, Object value, String message);

    /**
     * Previously unseen categorical level has been detected
     *
     * @param columnName Name of the column to which the categorical value belongs
     * @param value      Original value
     * @param message    Reason and/or actions taken
     */
    public abstract void unseenCategorical(String columnName, Object value, String message);
  }

  /**
   * Configuration builder for instantiating a Wrapper.
   */
  public static class Config {
    private GenModel model;
    private boolean convertUnknownCategoricalLevelsToNa = false;
    private boolean convertInvalidNumbersToNa = false;
    private boolean useExtendedOutput = false;
    private ErrorConsumer errorConsumer;
    private boolean enableLeafAssignment = false;  // default to false
    private boolean enableGLRMReconstrut = false;
    private boolean enableStagedProbabilities = false;
    private boolean enableContributions = false;
    private boolean useExternalEncoding = false;
    private int glrmIterNumber = 100; // default set to 100

    /**
     * Specify model object to wrap.
     *
     * @param value model
     * @return this config object
     */
    public Config setModel(GenModel value) {
      model = value;
      return this;
    }

    /**
     * @return model object being wrapped
     */
    public GenModel getModel() { return model; }

    /**
     * Specify how to handle unknown categorical levels.
     *
     * @param value false: throw exception; true: convert to N/A
     * @return this config object
     */
    public Config setConvertUnknownCategoricalLevelsToNa(boolean value) {
      convertUnknownCategoricalLevelsToNa = value;
      return this;
    }

    public Config setEnableLeafAssignment(boolean val) throws IOException {
      if (val && (model==null))
        throw new IOException("enableLeafAssignment cannot be set with null model.  Call setModel() first.");
      if (val && !(model instanceof TreeBackedMojoModel))
        throw new IOException("enableLeafAssignment can be set to true only with TreeBackedMojoModel," +
                " i.e. with GBM, DRF, Isolation forest or XGBoost.");

      enableLeafAssignment = val;
      return this;
    }

    public Config setEnableGLRMReconstrut(boolean value) throws IOException {
      if (value && (model==null))
        throw new IOException("Cannot set enableGLRMReconstruct for a null model.  Call config.setModel() first.");

      if (value && !(model instanceof GlrmMojoModel))
        throw new IOException("enableGLRMReconstruct shall only be used with GlrmMojoModels.");
      enableGLRMReconstrut = value;
      return this;
    }

    public Config setGLRMIterNumber(int value) throws IOException {
      if (model==null)
        throw new IOException("Cannot set glrmIterNumber for a null model.  Call config.setModel() first.");

      if (!(model instanceof GlrmMojoModel))
        throw new IOException("glrmIterNumber  shall only be used with GlrmMojoModels.");
      
      if (value <= 0)
        throw new IllegalArgumentException("GLRMIterNumber must be positive.");
      glrmIterNumber = value;
      return this;
    }

    public Config setEnableStagedProbabilities (boolean val) throws IOException {
        if (val && (model==null))
            throw new IOException("enableStagedProbabilities cannot be set with null model.  Call setModel() first.");
        if (val && !(model instanceof SharedTreeMojoModel))
            throw new IOException("enableStagedProbabilities can be set to true only with SharedTreeMojoModel," +
                    " i.e. with GBM or DRF.");

        enableStagedProbabilities  = val;
        return this;
    }

    public boolean getEnableGLRMReconstrut() { return enableGLRMReconstrut; }

    public Config setEnableContributions(boolean val) throws IOException {
      if (val && (model==null))
        throw new IOException("setEnableContributions cannot be set with null model.  Call setModel() first.");
      if (val && !(model instanceof PredictContributionsFactory))
        throw new IOException("setEnableContributions can be set to true only with DRF, GBM, or XGBoost models.");
      if (val && (ModelCategory.Multinomial.equals(model.getModelCategory()))) {
        throw new IOException("setEnableContributions is not yet supported for multinomial classification models.");
      }
      enableContributions = val;
      return this;
    }

    public boolean getEnableContributions() { return enableContributions; }

    /**
     * Allows to switch on/off applying categorical encoding in EasyPredictModelWrapper.
     * In current implementation only AUTO encoding is supported by the Wrapper, users are required to set
     * this flag to true if they want to use POJOs/MOJOs with other encodings than AUTO.
     * 
     * This requirement will be removed in https://0xdata.atlassian.net/browse/PUBDEV-6929 
     * @param val if true, user needs to provide already encoded input in the RowData structure
     * @return self
     */
    public Config setUseExternalEncoding(boolean val)  {
      useExternalEncoding = val;
      return this;
    }

    public boolean getUseExternalEncoding() { return useExternalEncoding; }

    /**
     * @return Setting for unknown categorical levels handling
     */
    public boolean getConvertUnknownCategoricalLevelsToNa() { return convertUnknownCategoricalLevelsToNa; }

    public int getGLRMIterNumber() { return glrmIterNumber; }

    /**
     * Specify the default action when a string value cannot be converted to
     * a number.
     *
     * @param value if true, then an N/A value will be produced, if false an
     *              exception will be thrown.
     */
    public Config setConvertInvalidNumbersToNa(boolean value) {
      convertInvalidNumbersToNa = value;
      return this;
    }

    public boolean getConvertInvalidNumbersToNa() {
      return convertInvalidNumbersToNa;
    }

    /**
     * Specify whether to include additional metadata in the prediction output.
     * This feature needs to be supported by a particular model and type of metadata
     * is model specific.
     *
     * @param value if true, then the Prediction result will contain extended information
     *              about the prediction (this will be specific to a particular model).
     * @return this config object
     */
    public Config setUseExtendedOutput(boolean value) {
      useExtendedOutput = value;
      return this;
    }

    public boolean getUseExtendedOutput() {
      return useExtendedOutput;
    }

    public boolean getEnableLeafAssignment() { return enableLeafAssignment;}

    public boolean getEnableStagedProbabilities() { return enableStagedProbabilities;}

    /**
     * @return An instance of ErrorConsumer used to build the {@link EasyPredictModelWrapper}. Null if there is no instance.
     */
    public ErrorConsumer getErrorConsumer() {
      return errorConsumer;
    }

    /**
     * Specify an instance of {@link ErrorConsumer} the {@link EasyPredictModelWrapper} is going to call
     * whenever an error defined by the {@link ErrorConsumer} instance occurs.
     *
     * @param errorConsumer An instance of {@link ErrorConsumer}
     * @return This {@link Config} object
     */
    public Config setErrorConsumer(final ErrorConsumer errorConsumer) {
      this.errorConsumer = errorConsumer;
      return this;
    }
  }

  /**
   * Create a wrapper for a generated model.
   *
   * @param config The wrapper configuration
   */
  public EasyPredictModelWrapper(Config config) {
    m = config.getModel();
    // Ensure an error consumer is always instantiated to avoid missing null-check errors.
    ErrorConsumer errorConsumer = config.getErrorConsumer() == null ? new VoidErrorConsumer() : config.getErrorConsumer();

    // How to handle unknown categorical levels.
    useExtendedOutput = config.getUseExtendedOutput();
    enableLeafAssignment = config.getEnableLeafAssignment();
    enableGLRMReconstruct = config.getEnableGLRMReconstrut();
    enableStagedProbabilities = config.getEnableStagedProbabilities();
    enableContributions = config.getEnableContributions();
    glrmIterNumber = config.getGLRMIterNumber();

    if (m instanceof GlrmMojoModel)
      ((GlrmMojoModel)m)._iterNumber=glrmIterNumber;
    if (enableContributions) {
      if (!(m instanceof PredictContributionsFactory)) {
        throw new IllegalStateException("Model " + m.getClass().getName() + " cannot be used to predict contributions.");
      }
      predictContributions = ((PredictContributionsFactory) m).makeContributionsPredictor();
    } else {
      predictContributions = null;
    }

    CategoricalEncoding categoricalEncoding = config.getUseExternalEncoding() ?
            CategoricalEncoding.AUTO : m.getCategoricalEncoding();
    Map<String, Integer> columnMapping = categoricalEncoding.createColumnMapping(m);
    Map<Integer, CategoricalEncoder> domainMap = categoricalEncoding.createCategoricalEncoders(m, columnMapping);

    if (m instanceof ConverterFactoryProvidingModel) {
      rowDataConverter = ((ConverterFactoryProvidingModel) m).makeConverterFactory(columnMapping, domainMap, errorConsumer, config);
    } else {
      rowDataConverter = new RowToRawDataConverter(m, columnMapping, domainMap, errorConsumer, config);
    }
  }


  /**
   * Create a wrapper for a generated model.
   *
   * @param model The generated model
   */
  public EasyPredictModelWrapper(GenModel model) {
    this(new Config()
            .setModel(model));
  }


  /**
   * Make a prediction on a new data point.
   *
   * The type of prediction returned depends on the model type.
   * The caller needs to know what type of prediction to expect.
   *
   * This call is convenient for generically automating model deployment.
   * For specific applications (where the kind of model is known and doesn't change), it is recommended to call
   * specific prediction calls like predictBinomial() directly.
   *
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public AbstractPrediction predict(RowData data, ModelCategory mc) throws PredictException {
    switch (mc) {
      case AutoEncoder:
        return predictAutoEncoder(data);
      case Binomial:
        return predictBinomial(data);
      case Multinomial:
        return predictMultinomial(data);
      case Ordinal:
        return predictOrdinal(data);
      case Clustering:
        return predictClustering(data);
      case Regression:
        return predictRegression(data);
      case DimReduction:
        return predictDimReduction(data);
      case WordEmbedding:
        return predictWord2Vec(data);
      case TargetEncoder:
        return predictTargetEncoding(data);
      case AnomalyDetection:
        return predictAnomalyDetection(data);
      case KLime:
        return predictKLime(data);
      case Unknown:
        throw new PredictException("Unknown model category");
      default:
        throw new PredictException("Unhandled model category (" + m.getModelCategory() + ") in switch statement");
    }
  }

  public AbstractPrediction predict(RowData data) throws PredictException {
    return predict(data, m.getModelCategory());
  }

  ErrorConsumer getErrorConsumer() {
    return rowDataConverter.getErrorConsumer();
  }

  /**
   * Returns names of contributions for prediction results with constributions enabled. 
   * @return array of contribution names (array has same lenght as the actual contributions, last is BiasTerm)
   */
  public String[] getContributionNames() {
    if (predictContributions == null) {
      throw new IllegalStateException(
              "Contributions were not enabled using in EasyPredictModelWrapper (use setEnableContributions).");
    }
    return predictContributions.getContributionNames();
  }

  /**
   * Make a prediction on a new data point using an AutoEncoder model.
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public AutoEncoderModelPrediction predictAutoEncoder(RowData data) throws PredictException {
    validateModelCategory(ModelCategory.AutoEncoder);

    int size = m.getPredsSize(ModelCategory.AutoEncoder);
    double[] output = new double[size];
    double[] rawData = nanArray(m.nfeatures());
    rawData = fillRawData(data, rawData);
    output = m.score0(rawData, output);

    AutoEncoderModelPrediction p = new AutoEncoderModelPrediction();
    p.original = expandRawData(rawData, output.length);
    p.reconstructed = output;
    p.reconstructedRowData = reconstructedToRowData(output);
    if (m instanceof DeeplearningMojoModel){
      DeeplearningMojoModel mojoModel = ((DeeplearningMojoModel)m);
      p.mse =  mojoModel.calculateReconstructionErrorPerRowData(p.original, p.reconstructed);
    }
    return p;
  }

  /**
   * Creates a 1-hot encoded representation of the input data.
   * @param data raw input as seen by the score0 function
   * @param size target size of the output array
   * @return 1-hot encoded data
   */
  private double[] expandRawData(double[] data, int size) {
    double[] expanded = new double[size];
    int pos = 0;
    for (int i = 0; i < data.length; i++) {
      if (m._domains[i] == null) {
        expanded[pos] = data[i];
        pos++;
      } else {
        int idx = Double.isNaN(data[i]) ? m._domains[i].length : (int) data[i];
        expanded[pos + idx] = 1.0;
        pos += m._domains[i].length + 1;
      }
    }
    return expanded;
  }

  /**
   * Converts output of AutoEncoder to a RowData structure. Categorical fields are represented by
   * a map of domain values -> reconstructed values, missing domain value is represented by a 'null' key
   * @param reconstructed raw output of AutoEncoder
   * @return reconstructed RowData structure
   */
  private RowData reconstructedToRowData(double[] reconstructed) {
    RowData rd = new RowData();
    int pos = 0;
    for (int i = 0; i < m.nfeatures(); i++) {
      Object value;
      if (m._domains[i] == null) {
        value = reconstructed[pos++];
      } else {
        value = catValuesAsMap(m._domains[i], reconstructed, pos);
        pos += m._domains[i].length + 1;
      }
      rd.put(m._names[i], value);
    }
    return rd;
  }

  private static Map<String, Double> catValuesAsMap(String[] cats, double[] reconstructed, int offset) {
    Map<String, Double> result = new HashMap<>(cats.length + 1);
    for (int i = 0; i < cats.length; i++) {
      result.put(cats[i], reconstructed[i + offset]);
    }
    result.put(null, reconstructed[offset + cats.length]);
    return result;
  }

  /**
   * Make a prediction on a new data point using a Dimension Reduction model (PCA, GLRM)
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public DimReductionModelPrediction predictDimReduction(RowData data) throws PredictException {
    double[] preds = preamble(ModelCategory.DimReduction, data);  // preds contains the x factor

    DimReductionModelPrediction p = new DimReductionModelPrediction();
    p.dimensions = preds;
    if (m instanceof GlrmMojoModel && ((GlrmMojoModel) m)._archetypes_raw != null && this.enableGLRMReconstruct)  // only for verion 1.10 or higher
      p.reconstructed = ((GlrmMojoModel) m).impute_data(preds, new double[m.nfeatures()], ((GlrmMojoModel) m)._nnums,
              ((GlrmMojoModel) m)._ncats, ((GlrmMojoModel) m)._permutation, ((GlrmMojoModel) m)._reverse_transform,
              ((GlrmMojoModel) m)._normMul, ((GlrmMojoModel) m)._normSub, ((GlrmMojoModel) m)._losses,
              ((GlrmMojoModel) m)._transposed, ((GlrmMojoModel) m)._archetypes_raw, ((GlrmMojoModel) m)._catOffsets,
              ((GlrmMojoModel) m)._numLevels);
    return p;
  }

  /**
   * Calculate an aggregated word-embedding for a given input sentence (sequence of words).
   * 
   * @param sentence array of word forming a sentence
   * @return word-embedding for the given sentence calculated by averaging the embeddings of the input words
   * @throws PredictException if model is not a WordEmbedding model
   */
  public float[] predictWord2Vec(String[] sentence) throws PredictException {
    final WordEmbeddingModel weModel = asWordEmbeddingModel();
    final int vecSize = weModel.getVecSize();

    final float[] aggregated = new float[vecSize];
    final float[] current = new float[vecSize];
    int embeddings = 0;
    for (String word : sentence) {
      final float[] embedding = weModel.transform0(word, current);
      if (embedding == null)
        continue;
      embeddings++;
      for (int i = 0; i < vecSize; i++)
        aggregated[i] += embedding[i];
    }
    if (embeddings > 0) {
      for (int i = 0; i < vecSize; i++) {
        aggregated[i] /= (float) embeddings;
      }
    } else {
      Arrays.fill(aggregated, Float.NaN);
    }

    return aggregated;
  }

  /**
   * Lookup word embeddings for a given word (or set of words). The result is a dictionary of
   * words mapped to their respective embeddings.
   * 
   * @param data RawData structure, every key with a String value will be translated to an embedding,
   *             note: keys only purpose is to link the output embedding to the input word
   * @return The prediction
   * @throws PredictException if model is not a WordEmbedding model
   */
  public Word2VecPrediction predictWord2Vec(RowData data) throws PredictException {
    final WordEmbeddingModel weModel = asWordEmbeddingModel();
    final int vecSize = weModel.getVecSize();

    HashMap<String, float[]> embeddings = new HashMap<>(data.size());
    for (String wordKey : data.keySet()) {
      Object value = data.get(wordKey);
      if (value instanceof String) {
        String word = (String) value;
        embeddings.put(wordKey, weModel.transform0(word, new float[vecSize]));
      }
    }

    Word2VecPrediction p = new Word2VecPrediction();
    p.wordEmbeddings = embeddings;

    return p;

  }

  private WordEmbeddingModel asWordEmbeddingModel() throws PredictException {
    validateModelCategory(ModelCategory.WordEmbedding);

    if (! (m instanceof WordEmbeddingModel))
      throw new PredictException("Model is not of the expected type, class = " + m.getClass().getSimpleName());
    return  (WordEmbeddingModel) m;
  }
  
  /**
   * Make a prediction on a new data point using a Binomial model.
   *
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public AnomalyDetectionPrediction predictAnomalyDetection(RowData data) throws PredictException {
    double[] preds = preamble(ModelCategory.AnomalyDetection, data, 0.0);

    AnomalyDetectionPrediction p = new AnomalyDetectionPrediction(preds);
    if (enableLeafAssignment) { // only get leaf node assignment if enabled
      SharedTreeMojoModel.LeafNodeAssignments assignments = leafNodeAssignmentExtended(data);
      p.leafNodeAssignments = assignments._paths;
      p.leafNodeAssignmentIds = assignments._nodeIds;
    }
    if (enableStagedProbabilities) {
        double[] rawData = nanArray(m.nfeatures());
        rawData = fillRawData(data, rawData);
        p.stageProbabilities = ((SharedTreeMojoModel) m).scoreStagedPredictions(rawData, preds.length);
    }
    return p;
  }

  /**
   * Make a prediction on a new data point using a Binomial model.
   *
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public BinomialModelPrediction predictBinomial(RowData data) throws PredictException {
    return predictBinomial(data, 0.0);
  }

  /**
   * Make a prediction on a new data point using a Binomial model.
   *
   * @param data A new data point.
   * @param offset An offset for the prediction.
   * @return The prediction.
   * @throws PredictException
   */
  public BinomialModelPrediction predictBinomial(RowData data, double offset) throws PredictException {

    double[] preds = preamble(ModelCategory.Binomial, data, offset);

    BinomialModelPrediction p = new BinomialModelPrediction();
    if (enableLeafAssignment) { // only get leaf node assignment if enabled
      SharedTreeMojoModel.LeafNodeAssignments assignments = leafNodeAssignmentExtended(data);
      p.leafNodeAssignments = assignments._paths;
      p.leafNodeAssignmentIds = assignments._nodeIds;
    }
    double d = preds[0];
    p.labelIndex = (int) d;
    String[] domainValues = m.getDomainValues(m.getResponseIdx());
    if (domainValues == null && m.getNumResponseClasses() == 2)
      domainValues = new String[]{"0", "1"}; // quasibinomial
    p.label = domainValues[p.labelIndex];
    p.classProbabilities = new double[m.getNumResponseClasses()];
    System.arraycopy(preds, 1, p.classProbabilities, 0, p.classProbabilities.length);
    if (m.calibrateClassProbabilities(preds)) {
      p.calibratedClassProbabilities = new double[m.getNumResponseClasses()];
      System.arraycopy(preds, 1, p.calibratedClassProbabilities, 0, p.calibratedClassProbabilities.length);
    }
    if (enableStagedProbabilities) {
        double[] rawData = nanArray(m.nfeatures());
        rawData = fillRawData(data, rawData);
        p.stageProbabilities = ((SharedTreeMojoModel) m).scoreStagedPredictions(rawData, preds.length);
    }
    if (enableContributions) {
      double[] rawData = nanArray(m.nfeatures());
      rawData = fillRawData(data, rawData);
      p.contributions = predictContributions.calculateContributions(rawData);
    }
    return p;
  }

  /**
   * @deprecated Use {@link #predictTargetEncoding(RowData)} instead.
   */
  @Deprecated
  public TargetEncoderPrediction transformWithTargetEncoding(RowData data) throws PredictException{
    return predictTargetEncoding(data);
  }

  /**
   * Perform target encoding based on TargetEncoderMojoModel
   * @param data RowData structure with data for which we want to produce transformations
   * @return TargetEncoderPrediction with transformations ordered in accordance with corresponding categorical columns' indices in training data
   * @throws PredictException
   */
  public TargetEncoderPrediction predictTargetEncoding(RowData data) throws PredictException{
    if (! (m instanceof TargetEncoderMojoModel))
      throw new PredictException("Model is not of the expected type, class = " + m.getClass().getSimpleName());

    TargetEncoderMojoModel tem = (TargetEncoderMojoModel) this.m;
    double[] preds = new double[tem.getPredsSize()];
    TargetEncoderPrediction prediction = new TargetEncoderPrediction();
    prediction.transformations = predict(data, 0, preds);
    return prediction;
  }

  @SuppressWarnings("unused") // not used in this class directly, kept for backwards compatibility
  public String[] leafNodeAssignment(RowData data) throws PredictException {
    double[] rawData = nanArray(m.nfeatures());
    rawData = fillRawData(data, rawData);
    return ((TreeBackedMojoModel) m).getDecisionPath(rawData);
  }

  public SharedTreeMojoModel.LeafNodeAssignments leafNodeAssignmentExtended(RowData data) throws PredictException {
    double[] rawData = nanArray(m.nfeatures());
    rawData = fillRawData(data, rawData);
    return ((TreeBackedMojoModel) m).getLeafNodeAssignments(rawData);
  }

  /**
   * Make a prediction on a new data point using a Multinomial model.
   *
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public MultinomialModelPrediction predictMultinomial(RowData data) throws PredictException {
    return predictMultinomial(data, 0D);
  }

  /**
   * Make a prediction on a new data point using a Multinomial model.
   *
   * @param data A new data point.
   * @param offset Prediction offset
   * @return The prediction.
   * @throws PredictException
   */
  public MultinomialModelPrediction predictMultinomial(RowData data, double offset) throws PredictException {
    double[] preds = preamble(ModelCategory.Multinomial, data, offset);

    MultinomialModelPrediction p = new MultinomialModelPrediction();
    if (enableLeafAssignment) { // only get leaf node assignment if enabled
      SharedTreeMojoModel.LeafNodeAssignments assignments = leafNodeAssignmentExtended(data);
      p.leafNodeAssignments = assignments._paths;
      p.leafNodeAssignmentIds = assignments._nodeIds;
    }
    p.classProbabilities = new double[m.getNumResponseClasses()];
    p.labelIndex = (int) preds[0];
    String[] domainValues = m.getDomainValues(m.getResponseIdx());
    p.label = domainValues[p.labelIndex];
    System.arraycopy(preds, 1, p.classProbabilities, 0, p.classProbabilities.length);
    if (enableStagedProbabilities) {
        double[] rawData = nanArray(m.nfeatures());
        rawData = fillRawData(data, rawData);
        p.stageProbabilities = ((SharedTreeMojoModel) m).scoreStagedPredictions(rawData, preds.length);
    }
    return p;
  }

  /**
   * Make a prediction on a new data point using a Ordinal model.
   *
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public OrdinalModelPrediction predictOrdinal(RowData data) throws PredictException {
    return predictOrdinal(data, 0D);
  }

  /**
   * Make a prediction on a new data point using a Ordinal model.
   *
   * @param data A new data point.
   * @param offset Prediction offset
   * @return The prediction.
   * @throws PredictException
   */
  public OrdinalModelPrediction predictOrdinal(RowData data, double offset) throws PredictException {
    double[] preds = preamble(ModelCategory.Ordinal, data, offset);

    OrdinalModelPrediction p = new OrdinalModelPrediction();
    p.classProbabilities = new double[m.getNumResponseClasses()];
    p.labelIndex = (int) preds[0];
    String[] domainValues = m.getDomainValues(m.getResponseIdx());
    p.label = domainValues[p.labelIndex];
    System.arraycopy(preds, 1, p.classProbabilities, 0, p.classProbabilities.length);

    return p;
  }

  /**
   * Sort in descending order.
   */
  private SortedClassProbability[] sortByDescendingClassProbability(String[] domainValues, double[] classProbabilities) {
    assert (classProbabilities.length == domainValues.length);
    SortedClassProbability[] arr = new SortedClassProbability[domainValues.length];
    for (int i = 0; i < domainValues.length; i++) {
      arr[i] = new SortedClassProbability();
      arr[i].name = domainValues[i];
      arr[i].probability = classProbabilities[i];
    }
    Arrays.sort(arr, Collections.reverseOrder());
    return arr;
  }

  /**
   * A helper function to return an array of binomial class probabilities for a prediction in sorted order.
   * The returned array has the most probable class in position 0.
   *
   * @param p The prediction.
   * @return An array with sorted class probabilities.
   */
  public SortedClassProbability[] sortByDescendingClassProbability(BinomialModelPrediction p) {
    String[] domainValues = m.getDomainValues(m.getResponseIdx());
    double[] classProbabilities = p.classProbabilities;
    return sortByDescendingClassProbability(domainValues, classProbabilities);
  }


  /**
   * Make a prediction on a new data point using a Clustering model.
   *
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public ClusteringModelPrediction predictClustering(RowData data) throws PredictException {
    ClusteringModelPrediction p = new ClusteringModelPrediction();
    if (useExtendedOutput && (m instanceof IClusteringModel)) {
      IClusteringModel cm = (IClusteringModel) m;
      // setup raw input
      double[] rawData = nanArray(m.nfeatures());
      rawData = fillRawData(data, rawData);
      // get cluster assignment & distances
      final int k = cm.getNumClusters();
      p.distances = new double[k];
      p.cluster = cm.distances(rawData, p.distances);
    } else {
      double[] preds = preamble(ModelCategory.Clustering, data);
      p.cluster = (int) preds[0];
    }

    return p;
  }

  /**
   * Make a prediction on a new data point using a Regression model.
   *
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public RegressionModelPrediction predictRegression(RowData data) throws PredictException {
    return predictRegression(data, 0D);
  }

  /**
   * Make a prediction on a new data point using a Regression model.
   *
   * @param data A new data point.
   * @param offset Prediction offset
   * @return The prediction.
   * @throws PredictException
   */
  public RegressionModelPrediction predictRegression(RowData data, double offset) throws PredictException {
    double[] preds = preamble(ModelCategory.Regression, data, offset);

    RegressionModelPrediction p = new RegressionModelPrediction();
    if (enableLeafAssignment) { // only get leaf node assignment if enabled
      SharedTreeMojoModel.LeafNodeAssignments assignments = leafNodeAssignmentExtended(data);
      p.leafNodeAssignments = assignments._paths;
      p.leafNodeAssignmentIds = assignments._nodeIds;
    }
    p.value = preds[0];
    if (enableStagedProbabilities) {
        double[] rawData = nanArray(m.nfeatures());
        rawData = fillRawData(data, rawData);
        p.stageProbabilities = ((SharedTreeMojoModel) m).scoreStagedPredictions(rawData, preds.length);
    }
    if (enableContributions) {
      double[] rawData = nanArray(m.nfeatures());
      rawData = fillRawData(data, rawData);
      p.contributions = predictContributions.calculateContributions(rawData);
    }
    return p;
  }

  public KLimeModelPrediction predictKLime(RowData data) throws PredictException {
    double[] preds = preamble(ModelCategory.KLime, data);

    KLimeModelPrediction p = new KLimeModelPrediction();
    p.value = preds[0];
    p.cluster = (int) preds[1];
    p.reasonCodes = new double[preds.length - 2];
    System.arraycopy(preds, 2, p.reasonCodes, 0, p.reasonCodes.length);

    return p;
  }

  /**
   * See {@link VariableImportances#topN(int)}
   */
  public KeyValue[] getTopNImportantVariables(int n) {
    VariableImportances variableImportances;
    if (m instanceof MojoModel) {
      ModelAttributes attributes = ((MojoModel) m)._modelAttributes;
      if (attributes == null) {
        throw new IllegalStateException("Model attributes are not available. Did you load metadata from model? MojoModel.load(\"model\", true)");
      } else if (attributes instanceof IVariableImportancesHolder) {
        variableImportances = ((IVariableImportancesHolder) attributes).getVariableImportances();
      } else {
        throw new IllegalStateException("Model does not support variable importance");
      }
    } else {
      throw new IllegalStateException("Model does not support variable importance");
    }
    return variableImportances.topN(n);
  }

  //----------------------------------------------------------------------
  // Transparent methods passed through to GenModel.
  //----------------------------------------------------------------------

  /**
   * Get the category (type) of model.
   * @return The category.
   */
  public ModelCategory getModelCategory() {
    return m.getModelCategory();
  }

  /**
   * Get the array of levels for the response column.
   * "Domain" just means list of level names for a categorical (aka factor, enum) column.
   * If the response column is numerical and not categorical, this will return null.
   *
   * @return The array.
   */
  public String[] getResponseDomainValues() {
    return m.getDomainValues(m.getResponseIdx());
  }

  /**
   * Some autoencoder thing, I'm not sure what this does.
   * @return CSV header for autoencoder.
   */
  public String getHeader() {
    return m.getHeader();
  }

  //----------------------------------------------------------------------
  // Private methods below this line.
  //----------------------------------------------------------------------




  private void validateModelCategory(ModelCategory c) throws PredictException {
    if (!m.getModelCategories().contains(c))
      throw new PredictException(c + " prediction type is not supported for this model.");
  }

  // This should have been called predict(), because that's what it does
  protected double[] preamble(ModelCategory c, RowData data) throws PredictException {
    return preamble(c, data, 0.0);
  }
  protected double[] preamble(ModelCategory c, RowData data, double offset) throws PredictException {
    validateModelCategory(c);
    final int predsSize = m.getPredsSize(c);
    return predict(data, offset, new double[predsSize]);
  }

  protected double[] fillRawData(RowData data, double[] rawData) throws PredictException {
    return rowDataConverter.convert(data, rawData);
  }

  protected double[] predict(RowData data, double offset, double[] preds) throws PredictException {
    double[] rawData = nanArray(m.nfeatures());
    rawData = fillRawData(data, rawData);
    if (m.requiresOffset() || offset != 0) {
      preds = m.score0(rawData, offset, preds);
    }
    else {
      preds = m.score0(rawData, preds);
    }
    return preds;
  }

}
