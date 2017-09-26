package hex.genmodel.easy;

import hex.ModelCategory;
import hex.genmodel.GenModel;
import hex.genmodel.algos.deepwater.DeepwaterMojoModel;
import hex.genmodel.algos.word2vec.WordEmbeddingModel;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.exception.PredictNumberFormatException;
import hex.genmodel.easy.exception.PredictUnknownCategoricalLevelException;
import hex.genmodel.easy.exception.PredictUnknownTypeException;
import hex.genmodel.easy.prediction.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
  public final GenModel m;
  private final HashMap<String, Integer> modelColumnNameToIndexMap;
  private final HashMap<Integer, HashMap<String, Integer>> domainMap;

  private final boolean convertUnknownCategoricalLevelsToNa;
  private final boolean convertInvalidNumbersToNa;
  private final ConcurrentHashMap<String,AtomicLong> unknownCategoricalLevelsSeenPerColumn;

  /**
   * Configuration builder for instantiating a Wrapper.
   */
  public static class Config {
    private GenModel model;
    private boolean convertUnknownCategoricalLevelsToNa = false;
    private boolean convertInvalidNumbersToNa = false;

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
    convertInvalidNumbersToNa = config.getConvertInvalidNumbersToNa();
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
  public AbstractPrediction predict(RowData data, ModelCategory mc) throws PredictException {
    switch (mc) {
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
      case WordEmbedding:
        return predictWord2Vec(data);

      case Unknown:
        throw new PredictException("Unknown model category");
      default:
        throw new PredictException("Unhandled model category (" + m.getModelCategory() + ") in switch statement");
    }
  }

  public AbstractPrediction predict(RowData data) throws PredictException {
    return predict(data, m.getModelCategory());
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
    double[] preds = preamble(ModelCategory.DimReduction, data);

    DimReductionModelPrediction p = new DimReductionModelPrediction();
    p.dimensions = preds;

    return p;

  }
  /**
   * Lookup word embeddings for a given word (or set of words).
   * @param data RawData structure, every key with a String value will be translated to an embedding
   * @return The prediction
   * @throws PredictException if model is not a WordEmbedding model
   */
  public Word2VecPrediction predictWord2Vec(RowData data) throws PredictException {
    validateModelCategory(ModelCategory.WordEmbedding);

    if (! (m instanceof WordEmbeddingModel))
      throw new PredictException("Model is not of the expected type, class = " + m.getClass().getSimpleName());
    final WordEmbeddingModel weModel = (WordEmbeddingModel) m;
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
    double d = preds[0];
    p.labelIndex = (int) d;
    String[] domainValues = m.getDomainValues(m.getResponseIdx());
    p.label = domainValues[p.labelIndex];
    p.classProbabilities = new double[m.getNumResponseClasses()];
    System.arraycopy(preds, 1, p.classProbabilities, 0, p.classProbabilities.length);
    if (m.calibrateClassProbabilities(preds)) {
      p.calibratedClassProbabilities = new double[m.getNumResponseClasses()];
      System.arraycopy(preds, 1, p.calibratedClassProbabilities, 0, p.calibratedClassProbabilities.length);
    }
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
    if (!m.getModelCategories().contains(c))
      throw new PredictException(c + " prediction type is not supported for this model.");
  }

  // This should have been called predict(), because that's what it does
  private double[] preamble(ModelCategory c, RowData data) throws PredictException {
    validateModelCategory(c);
    return predict(data, new double[m.getPredsSize(c)]);
  }

  private static double[] nanArray(int len) {
    double[] arr = new double[len];
    for (int i = 0; i < len; i++) {
      arr[i] = Double.NaN;
    }
    return arr;
  }

  private double[] fillRawData(RowData data, double[] rawData) throws PredictException {

    // TODO: refactor
    boolean isImage = m instanceof DeepwaterMojoModel && ((DeepwaterMojoModel) m)._problem_type.equals("image");
    boolean isText  = m instanceof DeepwaterMojoModel && ((DeepwaterMojoModel) m)._problem_type.equals("text");

    for (String dataColumnName : data.keySet()) {
      Integer index = modelColumnNameToIndexMap.get(dataColumnName);

      // Skip column names that are not known.
      // Skip the "response" column which should not be included in `rawData`
      if (index == null || index >= rawData.length) {
        continue;
      }

      BufferedImage img = null;
      String[] domainValues = m.getDomainValues(index);
      if (domainValues == null) {
        // Column is either numeric or a string (for images or text)
        double value = Double.NaN;
        Object o = data.get(dataColumnName);
        if (o instanceof String) {
          String s = ((String) o).trim();
          // Url to an image given
          if (isImage) {
            boolean isURL = s.matches("^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");
            try {
              img = isURL? ImageIO.read(new URL(s)) : ImageIO.read(new File(s));
            }
            catch (IOException e) {
              throw new PredictException("Couldn't read image from " + s);
            }
          } else if (isText) {
            // TODO: use model-specific vectorization of text
            throw new PredictException("MOJO scoring for text classification is not yet implemented.");
          }
          else {
            // numeric
            try {
              value = Double.parseDouble(s);
            } catch(NumberFormatException nfe) {
              if (!convertInvalidNumbersToNa)
                throw new PredictNumberFormatException("Unable to parse value: " + s + ", from column: "+ dataColumnName + ", as Double; " + nfe.getMessage());
            }
          }
        } else if (o instanceof Double) {
          value = (Double) o;
        } else if (o instanceof byte[] && isImage) {
          // Read the image from raw bytes
          InputStream is = new ByteArrayInputStream((byte[]) o);
          try {
            img = ImageIO.read(is);
          } catch (IOException e) {
            throw new PredictException("Couldn't interpret raw bytes as an image.");
          }
        } else {
          throw new PredictUnknownTypeException(
                  "Unexpected object type " + o.getClass().getName() + " for numeric column " + dataColumnName);
        }

        if (isImage && img != null) {
          DeepwaterMojoModel dwm = (DeepwaterMojoModel) m;
          int W = dwm._width;
          int H = dwm._height;
          int C = dwm._channels;
          float[] _destData = new float[W * H * C];
          try {
            GenModel.img2pixels(img, W, H, C, _destData, 0, dwm._meanImageData);
          } catch (IOException e) {
            e.printStackTrace();
            throw new PredictException("Couldn't vectorize image.");
          }
          rawData = new double[_destData.length];
          for (int i = 0; i < rawData.length; ++i)
            rawData[i] = _destData[i];
          return rawData;
        }
        rawData[index] = value;
      }
      else {
        // Column has categorical value.
        Object o = data.get(dataColumnName);
        double value;
        if (o instanceof String) {
          String levelName = (String) o;
          HashMap<String, Integer> columnDomainMap = domainMap.get(index);
          Integer levelIndex = columnDomainMap.get(levelName);
          if (levelIndex == null) {
            levelIndex = columnDomainMap.get(dataColumnName + "." + levelName);
          }
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
        } else if (o instanceof Double && Double.isNaN((double)o)) {
          value = (double)o; //Missing factor is the only Double value allowed
        } else {
          throw new PredictUnknownTypeException(
                  "Unexpected object type " + o.getClass().getName() + " for categorical column " + dataColumnName);
        }
        rawData[index] = value;
      }
    }
    return rawData;
  }

  private double[] predict(RowData data, double[] preds) throws PredictException {
    double[] rawData = nanArray(m.nfeatures());
    rawData = fillRawData(data, rawData);
    preds = m.score0(rawData, preds);
    return preds;
  }

}
