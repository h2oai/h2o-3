package hex.genmodel.easy;

import hex.ModelCategory;
import hex.genmodel.GenModel;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.exception.PredictUnknownCategoricalLevelException;
import hex.genmodel.easy.exception.PredictUnknownTypeException;
import hex.genmodel.easy.exception.PredictWrongModelCategoryException;
import hex.genmodel.easy.prediction.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
 * If you choose to convert unknown categorical levels to N/A, you can see how many times this is happening
 * with the following methods:
 *
 *     getTotalUnknownCategoricalLevelsSeen()
 *     getUnknownCategoricalLevelsSeenPerColumn()
 *
 * <p></p>
 * See the top-of-tree master version of this file <a href="https://github.com/h2oai/h2o-3/blob/master/h2o-genmodel/src/main/java/hex/genmodel/easy/EasyPredictModelWrapper.java" target="_blank">here on github</a>.
 */
public class EasyPredictModelWrapper implements java.io.Serializable {
  // These private members are read-only after the constructor.
  private final GenModel m;
  private final HashMap<String, Integer> modelColumnNameToIndexMap;
  private final HashMap<Integer, HashMap<String, Integer>> domainMap;

  // These private members are configured by setConvertUnknownCategoricalLevelsToNa().
  private final boolean convertUnknownCategoricalLevelsToNa;
  private final ConcurrentHashMap<String,AtomicLong> unknownCategoricalLevelsSeenPerColumn;

  /**
   * Configuration builder for instantiating a Wrapper.
   */
  public static class Config {
    private GenModel model;
    private boolean convertUnknownCategoricalLevelsToNa = false;

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

    /**
     * @return Setting for unknown categorical levels handling
     */
    public boolean getConvertUnknownCategoricalLevelsToNa() { return convertUnknownCategoricalLevelsToNa; }
  }

  /**
   * Create a wrapper for a generated model.
   *
   * @param config The wrapper configuration
   */
  public EasyPredictModelWrapper(Config config) {
    m = config.getModel();

    // Create map of column names to index number.
    modelColumnNameToIndexMap = new HashMap<>();
    String[] modelColumnNames = m.getNames();
    for (int i = 0; i < modelColumnNames.length; i++) {
      modelColumnNameToIndexMap.put(modelColumnNames[i], i);
    }

    // How to handle unknown categorical levels.
    unknownCategoricalLevelsSeenPerColumn = new ConcurrentHashMap<>();
    convertUnknownCategoricalLevelsToNa = config.getConvertUnknownCategoricalLevelsToNa();
    setupConvertUnknownCategoricalLevelsToNa();

    // Create map of input variable domain information.
    // This contains the categorical string to numeric mapping.
    domainMap = new HashMap<>();
    for (int i = 0; i < m.getNumCols(); i++) {
      String[] domainValues = m.getDomainValues(i);
      if (domainValues != null) {
        HashMap<String, Integer> m = new HashMap<>();
        for (int j = 0; j < domainValues.length; j++) {
          m.put(domainValues[j], j);
        }

        domainMap.put(i, m);
      }
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
   * Get the total number unknown categorical levels seen.
   *
   * A single prediction may contribute more than one to the count.
   * The count is only updated when setConvertUnknownCategoricalLevelsToNa is set to true.
   *
   * @return A long value.
   */
  public long getTotalUnknownCategoricalLevelsSeen() {
    ConcurrentHashMap<String, AtomicLong> map = getUnknownCategoricalLevelsSeenPerColumn();
    long total = 0;
    for (AtomicLong l : map.values()) {
      total += l.get();
    }
    return total;
  }

  /**
   * Get unknown categorical level counts.
   *
   * A single prediction may contribute to more than one count.
   * Counts are only updated when setConvertUnknownCategoricalLevelsToNa is set to true.
   *
   * @return A hash map with a per-column count of unknown categorical levels seen when making predictions.
   */
  public ConcurrentHashMap<String, AtomicLong> getUnknownCategoricalLevelsSeenPerColumn() {
    return unknownCategoricalLevelsSeenPerColumn;
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
  public AbstractPrediction predict(RowData data) throws PredictException {
    switch (m.getModelCategory()) {
      case AutoEncoder:
        return predictAutoEncoder(data);
      case Binomial:
        return predictBinomial(data);
      case Multinomial:
        return predictMultinomial(data);
      case Clustering:
        return predictClustering(data);
      case Regression:
        return predictRegression(data);
      case DimReduction:
        return predictDimReduction(data);

      case Unknown:
        throw new PredictException("Unknown model category");
      default:
        throw new PredictException("Unhandled model category (" + m.getModelCategory() + ") in switch statement");
    }
  }

  /**
   * Make a prediction on a new data point using an AutoEncoder model.
   *
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public AutoEncoderModelPrediction predictAutoEncoder(RowData data) throws PredictException {
    double[] preds = preamble(ModelCategory.AutoEncoder, data);
    throw new RuntimeException("Unimplemented " + preds.length);
  }
  /**
   * Make a prediction on a new data point using a Dimension Reduction model (PCA, GLRM)
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public DimReductionModelPrediction predictDimReduction(RowData data) throws PredictException {
    double[] preds = preamble(ModelCategory.DimReduction, data);

    DimReductionModelPrediction p = new DimReductionModelPrediction();
    p.dimensions = preds;

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
    double[] preds = preamble(ModelCategory.Binomial, data);

    BinomialModelPrediction p = new BinomialModelPrediction();
    p.classProbabilities = new double[m.getNumResponseClasses()];
    double d = preds[0];
    p.labelIndex = (int) d;
    String[] domainValues = m.getDomainValues(m.getResponseIdx());
    p.label = domainValues[p.labelIndex];
    System.arraycopy(preds, 1, p.classProbabilities, 0, p.classProbabilities.length);

    return p;
  }

  /**
   * Make a prediction on a new data point using a Multinomial model.
   *
   * @param data A new data point.
   * @return The prediction.
   * @throws PredictException
   */
  public MultinomialModelPrediction predictMultinomial(RowData data) throws PredictException {
    double[] preds = preamble(ModelCategory.Multinomial, data);

    MultinomialModelPrediction p = new MultinomialModelPrediction();
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
   * A helper function to return an array of multinomial class probabilities for a prediction in sorted order.
   * The returned array has the most probable class in position 0.
   *
   * @param p The prediction.
   * @return An array with sorted class probabilities.
   */
  public SortedClassProbability[] sortByDescendingClassProbability(MultinomialModelPrediction p) {
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
    double[] preds = preamble(ModelCategory.Clustering, data);

    ClusteringModelPrediction p = new ClusteringModelPrediction();
    p.cluster = (int) preds[0];

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
    double[] preds = preamble(ModelCategory.Regression, data);

    RegressionModelPrediction p = new RegressionModelPrediction();
    p.value = preds[0];

    return p;
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

  private void setupConvertUnknownCategoricalLevelsToNa() {
    if (convertUnknownCategoricalLevelsToNa) {
      for (int i = 0; i < m.getNumCols(); i++) {
        String[] domainValues = m.getDomainValues(i);
        if (domainValues != null) {
          String columnName = m.getNames()[i];
          unknownCategoricalLevelsSeenPerColumn.put(columnName, new AtomicLong());
        }
      }
    }
    else {
      unknownCategoricalLevelsSeenPerColumn.clear();
    }
  }

  private void validateModelCategory(ModelCategory c) throws PredictException {
    if (m.getModelCategory() != c) {
      throw new PredictWrongModelCategoryException("Prediction type unsupported by model of category " + m.getModelCategory());
    }
  }

  private double[] preamble(ModelCategory c, RowData data) throws PredictException {
    validateModelCategory(c);
    double[] preds;
    if (c == ModelCategory.DimReduction) {
      preds = new double[m.nclasses()];
    } else {
      preds = new double[m.getPredsSize()];
    }
    preds = predict(data, preds);
    return preds;
  }

  private void setToNaN(double[] arr) {
    for (int i = 0; i < arr.length; i++) {
      arr[i] = Double.NaN;
    }
  }

  private void fillRawData(RowData data, double[] rawData) throws PredictException {
    for (String dataColumnName : data.keySet()) {
      Integer index = modelColumnNameToIndexMap.get(dataColumnName);

      // Skip column names that are not known.
      // Skip the "response" column which should not be included in `rawData`
      if (index == null || index >= rawData.length) {
        continue;
      }

      String[] domainValues = m.getDomainValues(index);
      if (domainValues == null) {
        // Column has numeric value.
        double value;
        Object o = data.get(dataColumnName);
        if (o instanceof String) {
          String s = (String) o;
          value = Double.parseDouble(s);
        }
        else if (o instanceof Double) {
          value = (Double) o;
        }
        else {
          throw new PredictUnknownTypeException("Unknown object type " + o.getClass().getName());
        }

        rawData[index] = value;
      }
      else {
        // Column has categorical value.
        Object o = data.get(dataColumnName);
        if (o instanceof String) {
          String levelName = (String) o;
          HashMap<String, Integer> columnDomainMap = domainMap.get(index);
          Integer levelIndex = columnDomainMap.get(levelName);
          double value;
          if (levelIndex == null) {
            if (convertUnknownCategoricalLevelsToNa) {
              value = Double.NaN;
              unknownCategoricalLevelsSeenPerColumn.get(dataColumnName).incrementAndGet();
            }
            else {
              throw new PredictUnknownCategoricalLevelException("Unknown categorical level (" + dataColumnName + "," + levelName + ")", dataColumnName, levelName);
            }
          }
          else {
            value = levelIndex;
          }

          rawData[index] = value;
        }
        else {
          throw new PredictUnknownTypeException("Unknown object type " + o.getClass().getName());
        }
      }
    }
  }

  private double[] predict(RowData data, double[] preds) throws PredictException {
    double[] rawData = new double[m.nfeatures()];
    setToNaN(rawData);
    fillRawData(data, rawData);
    preds = m.score0(rawData, preds);
    return preds;
  }
}
