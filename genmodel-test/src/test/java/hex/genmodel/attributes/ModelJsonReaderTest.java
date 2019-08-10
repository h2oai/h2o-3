package hex.genmodel.attributes;

import com.google.gson.JsonObject;
import hex.KeyValue;
import hex.Model;
import hex.ScoreKeeper;
import hex.generic.Generic;
import hex.generic.GenericModel;
import hex.generic.GenericModelParameters;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.Key;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ModelJsonReaderTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void readGFMModelParametersForClassificationCase() throws IOException{
    Scope.enter();
    GBMModel gbm = null;
    try {
      String responseColumn = "survived";
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      asFactor(fr, responseColumn);
      printOutColumnsMetadata(fr);
      
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._response_column = responseColumn;
      parms._score_tree_interval = 10;
      parms._ntrees = 1000;
      parms._max_depth = 5;
      parms._nfolds = 3;
      parms._build_tree_one_node = true;
      parms._distribution = DistributionFamily.multinomial;
      parms._stopping_tolerance = 0.001;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms._stopping_rounds = 5;
      String[] ignoredColumns = {"embarked"};
      parms._ignored_columns = ignoredColumns;
      parms._sample_rate_per_class = new double[]{0.5, 0.5};
      parms._class_sampling_factors = new float[]{0.5f, 0.5f};
      parms._calibration_frame = fr._key;
      parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;

      GBM modelBuilder = new GBM(parms);
      gbm = modelBuilder.trainModel().get();

      File gbmMojoFile =  File.createTempFile("mojo", "zip");

      ModelParameter[] modelParameters = getMojosModelParameters(gbm, gbmMojoFile);

      // Actual values
      assertEquals(parms._max_depth, (int) findParameterByName("max_depth", modelParameters).actual_value);
      assertEquals(parms._ntrees, (int) findParameterByName("ntrees", modelParameters).actual_value);
      assertEquals(parms._score_tree_interval, (int) findParameterByName("score_tree_interval", modelParameters).actual_value);
      assertEquals(parms._stopping_rounds, (int) findParameterByName("stopping_rounds", modelParameters).actual_value);
      assertEquals(parms._stopping_tolerance, (double) findParameterByName("stopping_tolerance", modelParameters).actual_value, 1e-5);
      assertEquals(parms._stopping_metric.toString(), findParameterByName("stopping_metric", modelParameters).actual_value);
      assertEquals(parms._distribution.toString(), findParameterByName("distribution", modelParameters).actual_value);
      assertEquals(parms._response_column, ((VecSpecifier) findParameterByName("response_column", modelParameters).actual_value)._column_name);
      assertEquals(parms._build_tree_one_node, findParameterByName("build_tree_one_node", modelParameters).actual_value);
      
      ModelParameter catEncodingParameter = findParameterByName("categorical_encoding", modelParameters);
      String[] expectedCatEncodingValues = ArrayUtils.toString(Model.Parameters.CategoricalEncodingScheme.values());
      Arrays.sort(expectedCatEncodingValues);
      Arrays.sort(catEncodingParameter.values);
      assertArrayEquals(expectedCatEncodingValues, catEncodingParameter.values);

      ModelParameter ignoredColumnsParameter = findParameterByName("ignored_columns", modelParameters);
      assertArrayEquals(ignoredColumns, (String[]) ignoredColumnsParameter.actual_value);
      assertArrayEquals(new String[]{"training_frame", "validation_frame"}, ignoredColumnsParameter.is_member_of_frames);
      assertArrayEquals(new String[]{"response_column", "weights_column", "offset_column", "fold_column"}, ignoredColumnsParameter.is_mutually_exclusive_with);
      
      assertArrayEquals(parms._sample_rate_per_class, (double[]) findParameterByName("sample_rate_per_class", modelParameters).actual_value, 1e-5);
      assertEquals(parms._calibration_frame.toString(), ((hex.genmodel.attributes.Key) findParameterByName("calibration_frame", modelParameters).actual_value)._name);
      assertEquals(parms._fold_assignment.toString(), findParameterByName("fold_assignment", modelParameters).actual_value);


    } finally {
      if (gbm != null) gbm.delete();
      Scope.exit();
    }

  }

  @Test
  public void readGFMModelParametersForRegressionCase() throws IOException{
    Scope.enter();
    GBMModel gbm = null;
    try {
      String responseColumn = "survived";
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._response_column = responseColumn;
      parms._distribution = DistributionFamily.gaussian;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.RMSE;
      parms._monotone_constraints = new KeyValue[] {new KeyValue("age", -1)};

      GBM modelBuilder = new GBM(parms);
      gbm = modelBuilder.trainModel().get();

      File gbmMojoFile =  File.createTempFile("mojo", "zip");

      ModelParameter[] modelParameters = getMojosModelParameters(gbm, gbmMojoFile);

      // Actual values
      hex.genmodel.attributes.KeyValue[] monotoneConstraintsFromMojo = (hex.genmodel.attributes.KeyValue[]) findParameterByName("monotone_constraints", modelParameters).actual_value;
      int idx = 0;
      for(hex.genmodel.attributes.KeyValue kv : monotoneConstraintsFromMojo) {
        assertEquals(parms._monotone_constraints[idx].getKey(), kv._key);
        assertEquals(parms._monotone_constraints[idx].getValue(), kv._value, 1e-5);
        idx++;
      }

    } finally {
      if (gbm != null) gbm.delete();
      Scope.exit();
    }

  }

  @Test
  public void readGenericModelParameters() throws IOException{
    Scope.enter();
    GBMModel gbm = null;
    Key mojo = null;
    GenericModel genericModel = null;
    try {
      String responseColumn = "survived";
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      asFactor(fr, responseColumn);

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._response_column = responseColumn;
      parms._score_tree_interval = 10;
      parms._ntrees = 1000;
      parms._max_depth = 5;
      parms._build_tree_one_node = true;
      parms._distribution = DistributionFamily.multinomial;
      parms._stopping_tolerance = 0.001;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms._stopping_rounds = 5;
      parms._ignored_columns = new String[]{"embarked"};

      GBM modelBuilder = new GBM(parms);
      gbm = modelBuilder.trainModel().get();

      File gbmMojoFile =  File.createTempFile("mojo", "zip");

      ModelParameter[] gbmMojoModelParameters = getMojosModelParameters(gbm, gbmMojoFile);

      // Create Generic model from given imported MOJO
      final GenericModelParameters genericModelParameters = new GenericModelParameters();
      mojo = importMojo(gbmMojoFile.getAbsolutePath());
      genericModelParameters._model_key = mojo;
      final Generic generic = new Generic(genericModelParameters);
      genericModel = generic.trainModel().get();

      File genericMojoFile =  File.createTempFile("mojo", "zip");
      ModelParameter[] genericMojoModelParameters = getMojosModelParameters(genericModel, genericMojoFile);
      
      // We don't want to assume that the order of parameters is the same and we also don't want to override equals method
      // in ModelParameter as there is no use case in production code yet.
      for(ModelParameter gbmMP : gbmMojoModelParameters) {
        ModelParameter genericMP = findParameterByName(gbmMP._name, genericMojoModelParameters);
        assertTrue(modelParametersAreEqual(gbmMP, genericMP));
      }

    } finally {
      if (gbm != null) gbm.delete();
      if (genericModel != null) genericModel.delete();
      if (mojo != null) Keyed.remove(mojo);
      Scope.exit();
    }
  }

  private ModelParameter[] getMojosModelParameters(Model gbm, File mojoFile) throws IOException {

    String path = mojoFile.getPath();
    gbm.getMojo().writeTo(new FileOutputStream(path));

    final MojoReaderBackend readerBackend = MojoReaderBackendFactory.createReaderBackend(new FileInputStream(mojoFile), MojoReaderBackendFactory.CachingStrategy.MEMORY);

    final JsonObject modelJson = ModelJsonReader.parseModelJson(readerBackend);

    return ModelJsonReader.readModelParameters(modelJson, "parameters");
  }


  private ModelParameter findParameterByName(String name, ModelParameter[] modelParameters) {
    ModelParameter matchingParam = null;
    for( ModelParameter parameter: modelParameters) {
      if(parameter._name.equals(name)) matchingParam = parameter;
    }
    return matchingParam;
  }

  private Key<Frame> importMojo(final String mojoAbsolutePath) {
    final ArrayList<String> keys = new ArrayList<>(1);
    H2O.getPM().importFiles(mojoAbsolutePath, "", new ArrayList<String>(), keys, new ArrayList<String>(),
            new ArrayList<String>());
    assertEquals(1, keys.size());
    return DKV.get(keys.get(0))._key;
  }
  
  private boolean modelParametersAreEqual(ModelParameter mp1, ModelParameter mp2) {
    boolean defaultValuesAreEqual = areObjectValuesEqual(mp1.default_value, mp2.default_value);
    boolean actualValuesAreEqual = areObjectValuesEqual(mp1.actual_value, mp2.actual_value);

    return mp1._name.equals(mp2._name) &&
    mp1._label.equals(mp2._label) &&
    mp1._help.equals(mp2._help) &&
    mp1._required == mp2._required &&
    mp1._type.equals(mp2._type) &&
    defaultValuesAreEqual &&
    actualValuesAreEqual &&
    mp1.level.equals(mp2.level);
  }

  private boolean areObjectValuesEqual(Object obj1, Object obj2) {
    boolean defaultValuesAreEqual = false;
    if(obj1 instanceof VecSpecifier) {
      VecSpecifier vecSpecifierMP1 = (VecSpecifier) obj1;
      VecSpecifier vecSpecifierMP2 = (VecSpecifier) obj2;
      defaultValuesAreEqual = vecSpecifierMP1._column_name.equals(vecSpecifierMP2._column_name) && 
              Arrays.equals(vecSpecifierMP1._is_member_of_frames, vecSpecifierMP2._is_member_of_frames);
    } else if(obj1 instanceof hex.genmodel.attributes.Key ) {
      defaultValuesAreEqual = ((hex.genmodel.attributes.Key )obj1)._name.equals(((hex.genmodel.attributes.Key )obj2)._name);
    } else if(obj1 instanceof String[]) {
      defaultValuesAreEqual = Arrays.equals((String[]) obj1, (String[]) obj2);
    } else if(obj1 == null && obj2 == null) {
      defaultValuesAreEqual = true;
    } else if((obj1 == null && obj2 != null) || (obj1 != null && obj2 == null)) {
      defaultValuesAreEqual = false;
    } else {
      defaultValuesAreEqual = obj1.equals(obj2);
    }
    return defaultValuesAreEqual;
  }
}
