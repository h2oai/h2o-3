package hex.genmodel.easy;

import hex.ModelCategory;
import hex.genmodel.CategoricalEncoding;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackendFactoryTest;
import hex.genmodel.algos.word2vec.WordEmbeddingModel;
import hex.genmodel.attributes.parameters.KeyValue;
import hex.genmodel.easy.error.CountingErrorConsumer;
import hex.genmodel.easy.error.VoidErrorConsumer;
import hex.genmodel.easy.exception.PredictUnknownCategoricalLevelException;
import hex.genmodel.easy.prediction.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static hex.genmodel.utils.SerializationTestHelper.deserialize;
import static hex.genmodel.utils.SerializationTestHelper.serialize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class EasyPredictModelWrapperTest {

  private GenModel mockGenModel;

  @Before
  public void setUp(){
    mockGenModel = mock(GenModel.class);
    String[][] domains = {
        {"c1level1", "c1level2"},
        {"c2level1", "c2level2", "c2level3"},
        {"NO", "YES"}};
    when(mockGenModel.getNames()).thenReturn(new String[]{"C1", "C2", "RESPONSE"});
    when(mockGenModel.score0(any(double[].class), any(double[].class)))
        .thenReturn(new double[]{1});
    when(mockGenModel.score0(any(double[].class),eq(1D), any(double[].class)))
        .thenReturn(new double[]{1});
    when(mockGenModel.getDomainValues(0)).thenReturn(domains[0]);
    when(mockGenModel.getDomainValues(1)).thenReturn(domains[1]);
    when(mockGenModel.getDomainValues(2)).thenReturn(domains[2]);
    when(mockGenModel.getCategoricalEncoding()).thenReturn(CategoricalEncoding.AUTO);
  }

  private static class SupervisedModel extends GenModel {

    /**
     * Supervised model for testing purpose
     */
    SupervisedModel(String[] names, String[][] domains) {
      super(names, domains, names[names.length - 1]);
    }

    @Override
    public int nclasses() {
      return 2;
    }

    @Override
    public boolean isSupervised() {
      return true;
    }

    @Override
    public double[] score0(double[] data, double[] preds) {
      Assert.assertEquals(preds.length, 3);
      preds[0] = 0;
      preds[1] = 1.0;
      preds[2] = 0.0;
      return preds;
    }

    @Override
    public ModelCategory getModelCategory() {
      return ModelCategory.Binomial;
    }

    @Override
    public String getUUID() {
      return null;
    }
  }

  private static class UnsupervisedModel extends GenModel {

    /**
     * Supervised model for testing purpose
     */
    UnsupervisedModel(String[] names, String[][] domains) {
      super(names, domains, null);
    }

    @Override
    public int nclasses() {
      return 2;
    }

    @Override
    public boolean isSupervised() {
      return false;
    }

    @Override
    public double[] score0(double[] data, double[] preds) {
      Assert.assertEquals(preds.length, 2);
      preds[0] = 0;
      preds[1] = 1.0;
      return preds;
    }

    @Override
    public ModelCategory getModelCategory() {
      return ModelCategory.Clustering;
    }

    @Override
    public String getUUID() {
      return null;
    }
  }

  private static SupervisedModel makeSupervisedModel() {
    String[] names = {
            "C1",
            "C2",
            "RESPONSE"};
    String[][] domains = {
            {"c1level1", "c1level2"},
            {"c2level1", "c2level2", "c2level3"},
            {"NO", "YES"}
    };
    return new SupervisedModel(names, domains);
  }

    private static UnsupervisedModel makeUnsupervisedModel() {
      String[] names = {
          "C1",
          "C2"};
      String[][] domains = {
          {"c1level1", "c1level2"},
          {"c2level1", "c2level2", "c2level3"}
      };
      return new UnsupervisedModel(names, domains);
    }

  @Test
  public void testGetDataTransformationErrorsCount() throws Exception {
    SupervisedModel rawSupervisedModel = makeSupervisedModel();
    CountingErrorConsumer countingErrorConsumer = new CountingErrorConsumer(rawSupervisedModel);
    EasyPredictModelWrapper supervisedModel = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
        .setModel(rawSupervisedModel)
        .setErrorConsumer(countingErrorConsumer));

    RowData row = new RowData();
    row.put("C1", Double.NaN);
    supervisedModel.predictBinomial(row);

    Map<String, AtomicLong> errorsPerColumn = countingErrorConsumer.getDataTransformationErrorsCountPerColumn();
    Assert.assertNotNull(errorsPerColumn);
    Assert.assertEquals(2,errorsPerColumn.size());
    Assert.assertEquals(1,errorsPerColumn.get("C1").get());

    Assert.assertEquals(1, countingErrorConsumer.getDataTransformationErrorsCount());

    UnsupervisedModel rawModel = makeUnsupervisedModel();
    CountingErrorConsumer unsupervisedCounterErrorConsumer = new CountingErrorConsumer(rawModel);
    EasyPredictModelWrapper unsupervisedModel = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
        .setModel(rawModel)
        .setErrorConsumer(unsupervisedCounterErrorConsumer));

    unsupervisedModel.predict(row);

    Map<String, AtomicLong> errorsCountPerColumn = unsupervisedCounterErrorConsumer.getDataTransformationErrorsCountPerColumn();
    Assert.assertNotNull(errorsPerColumn);
    Assert.assertEquals(2, errorsCountPerColumn.size());
    Assert.assertEquals(1, errorsCountPerColumn.get("C1").get());

  }

  @Test
  public void testSerializeWrapper() throws Exception {
    SupervisedModel rawModel = makeSupervisedModel();
    EasyPredictModelWrapper m = new EasyPredictModelWrapper(rawModel);

    ensureAllFieldsSerializable(EasyPredictModelWrapper.class.getDeclaredFields());

    checkSerialization(m);
  }

  @Test
  public void testScoreOffsetBinomial() throws Exception {
    when(mockGenModel.getModelCategories()).thenReturn(EnumSet.of(ModelCategory.Binomial));
    EasyPredictModelWrapper model = new EasyPredictModelWrapper(mockGenModel);

    RowData row = new RowData();
    row.put("C1", Double.NaN);
    BinomialModelPrediction binomialModelPrediction = model.predictBinomial(row);
    Assert.assertNotNull(binomialModelPrediction);
    verify(mockGenModel).score0(any(double[].class),any(double[].class));

    BinomialModelPrediction predictionWithOffset = model.predictBinomial(row, 1D);
    Assert.assertNotNull(predictionWithOffset);
    verify(mockGenModel).score0(any(double[].class),eq(1D),any(double[].class));
  }

  @Test
  public void testScoreOffsetOrdinal() throws Exception {
    when(mockGenModel.getModelCategories()).thenReturn(EnumSet.of(ModelCategory.Ordinal));
    EasyPredictModelWrapper model = new EasyPredictModelWrapper(mockGenModel);

    RowData row = new RowData();
    row.put("C1", Double.NaN);
    OrdinalModelPrediction modelPrediction = model.predictOrdinal(row);
    Assert.assertNotNull(modelPrediction);
    verify(mockGenModel).score0(any(double[].class),any(double[].class));

    OrdinalModelPrediction predictionWithOffset = model.predictOrdinal(row, 1D);
    Assert.assertNotNull(predictionWithOffset);
    verify(mockGenModel).score0(any(double[].class),eq(1D),any(double[].class));
  }


  @Test
  public void testScoreOffsetMultinomial() throws Exception {
    when(mockGenModel.getModelCategories()).thenReturn(EnumSet.of(ModelCategory.Multinomial));
    EasyPredictModelWrapper model = new EasyPredictModelWrapper(mockGenModel);

    RowData row = new RowData();
    row.put("C1", Double.NaN);
    MultinomialModelPrediction modelPrediction = model.predictMultinomial(row);
    Assert.assertNotNull(modelPrediction);
    verify(mockGenModel).score0(any(double[].class),any(double[].class));

    MultinomialModelPrediction predictionWithOffset = model.predictMultinomial(row, 1D);
    Assert.assertNotNull(predictionWithOffset);
    verify(mockGenModel).score0(any(double[].class),eq(1D),any(double[].class));
  }

  @Test
  public void testScoreOffsetRegression() throws Exception {
    when(mockGenModel.getModelCategories()).thenReturn(EnumSet.of(ModelCategory.Regression));
    EasyPredictModelWrapper model = new EasyPredictModelWrapper(mockGenModel);

    RowData row = new RowData();
    row.put("C1", Double.NaN);
    RegressionModelPrediction modelPrediction = model.predictRegression(row);
    Assert.assertNotNull(modelPrediction);
    verify(mockGenModel).score0(any(double[].class),any(double[].class));

    RegressionModelPrediction predictionWithOffset = model.predictRegression(row, 1D);
    Assert.assertNotNull(predictionWithOffset);
    verify(mockGenModel).score0(any(double[].class),eq(1D),any(double[].class));
  }

  @Test
  public void testSerializeWrapperWithCountingConsumer() throws Exception {
    SupervisedModel rawModel = makeSupervisedModel();
    CountingErrorConsumer countingErrorConsumer = new CountingErrorConsumer(rawModel);
    EasyPredictModelWrapper m = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
            .setModel(rawModel)
            .setErrorConsumer(countingErrorConsumer));
    checkSerialization(m);
  }

  private static void checkSerialization(final EasyPredictModelWrapper m1) throws Exception {
    RowData row = new RowData() {{
      put("C1", "c1level1");
      put("C2", "c2level3");
    }};

    // serialize & deserialize wrapper
    EasyPredictModelWrapper m1deser = (EasyPredictModelWrapper) deserialize(serialize(m1));

    // check that the new wrapper can be used to predict
    BinomialModelPrediction p1 = (BinomialModelPrediction) m1.predict(row);
    BinomialModelPrediction p1deser = (BinomialModelPrediction) m1deser.predict(row);
    Assert.assertEquals(p1.label, p1deser.label);
  }

  @Test
  public void testUnknownCategoricalLevels() throws Exception {
    SupervisedModel rawModel = makeSupervisedModel();
    CountingErrorConsumer.Config consumerConfig = new CountingErrorConsumer.Config();
    consumerConfig.setCollectUnseenCategoricals(true);
    CountingErrorConsumer countingErrorConsumer = new CountingErrorConsumer(rawModel, consumerConfig);
    EasyPredictModelWrapper m = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
    .setModel(rawModel)
    .setErrorConsumer(countingErrorConsumer));

    {
      RowData row = new RowData();
      row.put("C1", "c1level1");
      try {
        m.predictBinomial(row);
      } catch (PredictUnknownCategoricalLevelException e) {
        Assert.fail("Caught exception but should not have");
      }
      Map<String, AtomicLong> unknown = countingErrorConsumer.getUnknownCategoricalsPerColumn();
      long total = 0;
      Set<Object> allUnseen = new HashSet<>();
      for (String colName : unknown.keySet()) {
        total += unknown.get(colName).get();
        allUnseen.addAll(countingErrorConsumer.getUnseenCategoricals(colName).keySet());
      }
      Assert.assertEquals(total, 0);
      Assert.assertEquals(allUnseen, Collections.emptySet());
    }

    {
      RowData row = new RowData();
      row.put("C1", "c1level1");
      row.put("C2", "unknownLevel");
      boolean caught = false;
      try {
        m.predictBinomial(row);
      } catch (PredictUnknownCategoricalLevelException e) {
        caught = true;
      }
      Assert.assertTrue(caught);
      Map<String, AtomicLong> unknown = countingErrorConsumer.getUnknownCategoricalsPerColumn();
      long total = 0;
      Set<Object> allUnseen = new HashSet<>();
      for (String colName : unknown.keySet()) {
        total += unknown.get(colName).get();
        allUnseen.addAll(countingErrorConsumer.getUnseenCategoricals(colName).keySet());
      }
      Assert.assertEquals(total, 0);
      Assert.assertEquals(allUnseen, Collections.emptySet());
    }

    CountingErrorConsumer errorConsumer = new CountingErrorConsumer(rawModel, consumerConfig);
    m = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
            .setModel(rawModel)
            .setErrorConsumer(errorConsumer)
            .setConvertUnknownCategoricalLevelsToNa(true)
            .setConvertInvalidNumbersToNa(true));

    {
      RowData row0 = new RowData();
      m.predict(row0);
      Assert.assertEquals(errorConsumer.getTotalUnknownCategoricalLevelsSeen(), 0);

      RowData row1 = new RowData();
      row1.put("C1", "c1level1");
      row1.put("C2", "unknownLevel");
      m.predictBinomial(row1);
      Assert.assertEquals(errorConsumer.getTotalUnknownCategoricalLevelsSeen(), 1);
      Assert.assertEquals(
              Collections.singletonMap("unknownLevel", 1L),
              toSimpleMap(errorConsumer.getUnseenCategoricals("C2"))
      );

      RowData row2 = new RowData();
      row2.put("C1", "c1level1");
      row2.put("C2", "c2level3");
      m.predictBinomial(row2);
      Assert.assertEquals(errorConsumer.getTotalUnknownCategoricalLevelsSeen(), 1);
      Assert.assertEquals(
              Collections.singletonMap("unknownLevel", 1L),
              toSimpleMap(errorConsumer.getUnseenCategoricals("C2"))
      );

      RowData row3 = new RowData();
      row3.put("C1", "c1level1");
      row3.put("unknownColumn", "unknownLevel");
      m.predictBinomial(row3);
      Assert.assertEquals(errorConsumer.getTotalUnknownCategoricalLevelsSeen(), 1);
      Assert.assertEquals(
              Collections.singletonMap("unknownLevel", 1L),
              toSimpleMap(errorConsumer.getUnseenCategoricals("C2"))
      );
      
      m.predictBinomial(row1);
      m.predictBinomial(row1);
      Assert.assertEquals(errorConsumer.getTotalUnknownCategoricalLevelsSeen(), 3);
      Assert.assertEquals(
              Collections.singletonMap("unknownLevel", 3L),
              toSimpleMap(errorConsumer.getUnseenCategoricals("C2"))
      );

      RowData row4 = new RowData();
      row4.put("C1", "unknownLevel");
      m.predictBinomial(row4);
      Assert.assertEquals(errorConsumer.getTotalUnknownCategoricalLevelsSeen(), 4);
      Assert.assertEquals(errorConsumer.getUnknownCategoricalsPerColumn().get("C1").get(), 1);
      Assert.assertEquals(errorConsumer.getUnknownCategoricalsPerColumn().get("C2").get(), 3);
      Assert.assertEquals(4, errorConsumer.getTotalUnknownCategoricalLevelsSeen());
      Assert.assertEquals(
              Collections.singletonMap("unknownLevel", 1L),
              toSimpleMap(errorConsumer.getUnseenCategoricals("C1"))
      );
    }
  }

  @Test
  public void testSortedClassProbability() throws Exception {
    SupervisedModel rawModel = makeSupervisedModel();
    EasyPredictModelWrapper m = new EasyPredictModelWrapper(rawModel);

    {
      RowData row = new RowData();
      row.put("C1", "c1level1");
      BinomialModelPrediction p = m.predictBinomial(row);
      SortedClassProbability[] arr = m.sortByDescendingClassProbability(p);
      Assert.assertEquals(arr[0].name, "NO");
      Assert.assertEquals(arr[0].probability, 1.0, 0.001);
      Assert.assertEquals(arr[1].name, "YES");
      Assert.assertEquals(arr[1].probability, 0.0, 0.001);
    }
  }

  @Test
  public void testWordEmbeddingModel() throws Exception {
    MyWordEmbeddingModel rawModel = new MyWordEmbeddingModel();
    EasyPredictModelWrapper m = new EasyPredictModelWrapper(rawModel);

    RowData row = new RowData();
    row.put("C0", -1); // should be ignored
    row.put("C1", "0.9,0.1");
    row.put("C2", "0.1,0.9");
    row.put("C3", "NA");

    Word2VecPrediction p = m.predictWord2Vec(row);

    Assert.assertFalse(p.wordEmbeddings.containsKey("C0"));
    Assert.assertArrayEquals(new float[]{0.9f, 0.1f}, p.wordEmbeddings.get("C1"), 0.0001f);
    Assert.assertArrayEquals(new float[]{0.1f, 0.9f}, p.wordEmbeddings.get("C2"), 0.0001f);
    Assert.assertTrue(p.wordEmbeddings.containsKey("C3"));
    Assert.assertNull(p.wordEmbeddings.get("C3"));
  }

  @Test
  public void testPredictAggregatedEmbeddings() throws Exception {
    MyWordEmbeddingModel rawModel = new MyWordEmbeddingModel();
    EasyPredictModelWrapper m = new EasyPredictModelWrapper(rawModel);

    String[] sentence = new String[]{"0.9,0.1", "0.1,0.3", "NA"};
    Assert.assertArrayEquals(new float[]{0.5f, 0.2f}, m.predictWord2Vec(sentence), 1e-5f);
    Assert.assertArrayEquals(new float[]{Float.NaN, Float.NaN}, m.predictWord2Vec(new String[]{"NA"}), 1e-5f);
  }
  
  private static class MyWordEmbeddingModel extends MojoModel implements WordEmbeddingModel {

    public MyWordEmbeddingModel() {
      super(new String[0], new String[0][], null);
    }

    @Override
    public int getVecSize() {
      return 2;
    }

    @Override
    public float[] transform0(String word, float[] output) {
      if (word.equals("NA"))
        return null;
      String[] words = word.split(",");
      for (int i = 0; i < words.length; i++)
        output[i] = Float.parseFloat(words[i]);
      return output;
    }

    @Override
    public double[] score0(double[] row, double[] preds) {
      throw new IllegalStateException("Should never be called");
    }

    @Override
    public ModelCategory getModelCategory() {
      return ModelCategory.WordEmbedding;
    }
  }

  @Test
  public void testAutoEncoderModel() throws Exception {
    MyAutoEncoderModel rawModel = new MyAutoEncoderModel();
    EasyPredictModelWrapper m = new EasyPredictModelWrapper(rawModel);

    RowData row = new RowData();
    row.put("Species", "versicolor");
    row.put("Sepal.Length", 7.0);
    row.put("Sepal.Width", 3.2);
    row.put("Petal.Length", 4.7);
    row.put("Petal.Width", 1.4);

    AbstractPrediction p = m.predict(row);
    Assert.assertTrue(p instanceof AutoEncoderModelPrediction);
    AutoEncoderModelPrediction aep = (AutoEncoderModelPrediction) p;
    Assert.assertArrayEquals(new double[]{0.0, 1.0, 0.0, 0.0, 7.0, 3.2, 4.7, 1.4}, aep.original, 0.01);
    Assert.assertArrayEquals(new double[]{0.0, 1.3124, 0.4864, 0.0, 6.1729, 3.0573, 17.8372, 1.1993}, aep.reconstructed, 0.001);
    Map<String, Object> expected = new HashMap<String, Object>() {{
      put("Petal.Length", 17.8372);
      put("Petal.Width", 1.1993);
      put("Sepal.Width", 3.0573);
      put("Sepal.Length", 6.1729);
      put("Species", new HashMap<String, Object>() {{
        put(null, 0.0);
        put("setosa", 0.0);
        put("virginica", 0.4864);
        put("versicolor", 1.3124);
       }});
    }};
    Assert.assertEquals(expected, aep.reconstructedRowData);
  }

  @Test
  public void testVoidErrorConsumerInitialized() {
    MyAutoEncoderModel model = new MyAutoEncoderModel();
    EasyPredictModelWrapper m = new EasyPredictModelWrapper(model);

    EasyPredictModelWrapper.ErrorConsumer errorConsumer = m.getErrorConsumer();
    Assert.assertNotNull(errorConsumer);
    Assert.assertEquals(VoidErrorConsumer.class, errorConsumer.getClass());
  }


  @Test
  public void testVoidErrorConsumerInitializedWithConfig() {
    MyAutoEncoderModel model = new MyAutoEncoderModel();
    EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
        .setModel(model));

    EasyPredictModelWrapper.ErrorConsumer errorConsumer = modelWrapper.getErrorConsumer();
    Assert.assertNotNull(errorConsumer);
    Assert.assertEquals(VoidErrorConsumer.class, errorConsumer.getClass());
  }

  @Test
  public void testVariableImportances() throws Exception {
    URL modelRes = MojoReaderBackendFactoryTest.class.getResource("algos/gbm/gbm_variable_importance.zip");

    MojoModel modelMojo = MojoModel.load(modelRes.getPath(), true);

    EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config().setModel(modelMojo);
    EasyPredictModelWrapper model = new EasyPredictModelWrapper(config);

    KeyValue[] variableImportances = model.varimp(2);

    Assert.assertEquals("Variable importance has different size of array", 2, variableImportances.length);
    Assert.assertEquals("Variables are not correctly sorted", "GLEASON", variableImportances[0].getKey());
    Assert.assertEquals("Variables are not correctly sorted", "PSA", variableImportances[1].getKey());

    variableImportances = model.varimp(14);
    Assert.assertEquals("Variable importance has different size of array", 7, variableImportances.length);
    Assert.assertEquals("Variables are not correctly sorted", "VOL", variableImportances[2].getKey());
    Assert.assertEquals("Variables are not correctly sorted", "AGE", variableImportances[3].getKey());

    variableImportances = model.varimp(-2);
    Assert.assertEquals("Variable importance has different size of array", 7, variableImportances.length);
    Assert.assertEquals("Variables are not correctly sorted", "DPROS", variableImportances[4].getKey());
    Assert.assertEquals("Variables are not correctly sorted", "RACE", variableImportances[5].getKey());

    variableImportances = model.varimp();
    Assert.assertEquals("Variable importance has different size of array", 7, variableImportances.length);
    Assert.assertEquals("Variables are not correctly sorted", "GLEASON", variableImportances[0].getKey());
    Assert.assertEquals("Variables are not correctly sorted", "DCAPS", variableImportances[6].getKey());
  }


  private static class MyAutoEncoderModel extends GenModel {

    private static final String[][] DOMAINS = new String[][] {
      /* Species */ new String[]{"setosa", "versicolor", "virginica"},
      /* Sepal.Length */ null,
      /* Sepal.Width */ null,
      /* Petal.Length */ null,
      /* Petal.Width */ null
    };

    private MyAutoEncoderModel() {
      super(new String[] {"Species", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width"}, DOMAINS, null);
    }

    @Override
    public ModelCategory getModelCategory() { return ModelCategory.AutoEncoder; }
    @Override
    public boolean isSupervised() { return false; }
    @Override
    public int nfeatures() { return 5; }
    @Override
    public int nclasses() { return 8; }
    @Override
    public String getUUID() { return null; }
    @Override
    public int getPredsSize() { return nclasses(); }

    @Override
    public double[] score0(double[] row, double[] preds) {
      // 7.0,3.2,4.7,1.4,"versicolor"
      final double[] result = {0,1.3124,0.4864,0,6.1729,3.0573,17.8372,1.1993};
      Assert.assertArrayEquals(new double[]{1.0,7.0,3.2,4.7,1.4}, row, 0.0001);
      Assert.assertEquals(result.length, preds.length);
      System.arraycopy(result, 0, preds, 0, result.length);
      return result;
    }
  }

  private static void ensureAllFieldsSerializable(Field[] declaredFields) {

    for (Field field : declaredFields) {
      Assert.assertFalse(Modifier.isTransient(field.getModifiers()));
      Assert.assertTrue(Serializable.class.isAssignableFrom(field.getDeclaringClass()));
    }
  }

  private static Map<String, Long> toSimpleMap(Map<Object, AtomicLong> map) {
    HashMap<String, Long> m = new HashMap<>();
    for (Map.Entry<Object, AtomicLong> entry : map.entrySet()) {
      m.put((String) entry.getKey(), entry.getValue().longValue());
    }
    return m;
  }
  
}
