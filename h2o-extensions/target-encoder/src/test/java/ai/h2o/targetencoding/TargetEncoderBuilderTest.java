package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoder.DataLeakageHandlingStrategy;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.*;
import water.rapids.Merge;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.Map;

import static ai.h2o.targetencoding.TargetEncoderFrameHelper.encodingMapCleanUp;
import static org.junit.Assert.assertTrue;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncoderBuilderTest {

  @Test
  public void getTargetEncodingMapByTrainingTEBuilder() {

    TargetEncoderModel targetEncoderModel = null;
    Map<String, Frame> teEncodingMap = null;
    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      
      String responseColumnName = "survived";
      asFactor(fr, responseColumnName);
      String[] teColumns = new String[]{ "home.dest", "embarked" };

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      targetEncoderParameters._blending = false;
      targetEncoderParameters._response_column = responseColumnName;
      targetEncoderParameters._ignored_columns = ignoredColumns(fr, ArrayUtils.append(teColumns, targetEncoderParameters._response_column));
      targetEncoderParameters.setTrain(fr._key);

      TargetEncoderBuilder builder = new TargetEncoderBuilder(targetEncoderParameters);
      targetEncoderModel = builder.trainModel().get();
       
      // Let's create encoding map by TargetEncoder directly
      TargetEncoder tec = new TargetEncoder(teColumns);

      Frame fr2 = parse_test_file("./smalldata/gbm_test/titanic.csv");
      asFactor(fr2, responseColumnName);
      Scope.track(fr2);

      teEncodingMap = tec.prepareEncodingMap(fr2, responseColumnName, null);
      areEncodingMapsIdentical(teEncodingMap, targetEncoderModel._output._target_encoding_map);

    } finally {
      encodingMapCleanUp(teEncodingMap);
      targetEncoderModel.remove();
      Scope.exit();
    }
  }

  @Test
  public void teColumnNameToMissingValuesPresenceMapIsComputedCorrectly() {

    TargetEncoderModel targetEncoderModel = null;
    Map<String, Frame> teEncodingMap = null;
    Scope.enter();
    try {
      String responseColumnName = "ColB";
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("home.dest", "embarked", responseColumnName)
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b"))
              .withDataForCol(1, ar("s", null))
              .withDataForCol(2, ar("yes", "no"))
              .build();

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      targetEncoderParameters._blending = false;
      targetEncoderParameters._response_column = responseColumnName;
      targetEncoderParameters._ignored_columns = ignoredColumns(fr, "home.dest", "embarked", targetEncoderParameters._response_column);
      targetEncoderParameters.setTrain(fr._key);
      targetEncoderParameters._ignore_const_cols = false;

      TargetEncoderBuilder builder = new TargetEncoderBuilder(targetEncoderParameters);
      targetEncoderModel = builder.trainModel().get();

      Map<String, Integer> teColumnNameToMissingValuesPresence = targetEncoderModel._output._column_name_to_missing_val_presence;
      assertTrue(teColumnNameToMissingValuesPresence.get("home.dest") == 0);
      assertTrue(teColumnNameToMissingValuesPresence.get("embarked") == 1);
    } finally {
      encodingMapCleanUp(teEncodingMap);
      if (targetEncoderModel != null) targetEncoderModel.remove();
      Scope.exit();
    }
  }

  @Test
  public void getTargetEncodingMapByTrainingTEBuilder_KFold_scenario(){

    Map<String, Frame> teEncodingMap = null;
    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);
      
      String foldColumnName = "fold_column";
      addKFoldColumn(fr, foldColumnName, 5, 1234L);
      String responseColumnName = "survived";
      asFactor(fr, responseColumnName);
      String[] teColumns = new String[]{ "home.dest", "embarked" };

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      targetEncoderParameters._blending = false;
      targetEncoderParameters._response_column = responseColumnName;
      targetEncoderParameters._fold_column = foldColumnName;
      targetEncoderParameters._ignored_columns = ignoredColumns(fr, 
              ArrayUtils.append(teColumns, targetEncoderParameters._response_column, targetEncoderParameters._fold_column));
      targetEncoderParameters.setTrain(fr._key);

      TargetEncoderBuilder builder = new TargetEncoderBuilder(targetEncoderParameters);
      TargetEncoderModel targetEncoderModel = builder.trainModel().get();
      Scope.track_generic(targetEncoderModel);

      //Stage 2: 
      // Let's create encoding map by TargetEncoder directly
      TargetEncoder tec = new TargetEncoder(teColumns);

      Frame fr2 = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr2);
      addKFoldColumn(fr2, foldColumnName, 5, 1234L);
      asFactor(fr2, responseColumnName);

      teEncodingMap = tec.prepareEncodingMap(fr2, responseColumnName, foldColumnName);
      areEncodingMapsIdentical(teEncodingMap, targetEncoderModel._output._target_encoding_map);

    } finally {
      encodingMapCleanUp(teEncodingMap);
      Scope.exit();
    }
  }

  @Test
  public void transform_KFold_scenario(){

    Map<String, Frame> teEncodingMap = null;
    try {
      Scope.enter();
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(fr);

      String foldColumnName = "fold_column";
      addKFoldColumn(fr, foldColumnName, 5, 1234L);
      String responseColumnName = "survived";
      asFactor(fr, responseColumnName);
      String[] teColumns = new String[]{ "home.dest", "embarked" };

      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
      targetEncoderParameters._blending = false;
      targetEncoderParameters._response_column = responseColumnName;
      targetEncoderParameters._fold_column = foldColumnName;
      targetEncoderParameters._seed = 1234;
      targetEncoderParameters._ignored_columns = ignoredColumns(fr,
              ArrayUtils.append(teColumns, targetEncoderParameters._response_column, targetEncoderParameters._fold_column));
      targetEncoderParameters._train = fr._key;

      TargetEncoderBuilder builder = new TargetEncoderBuilder(targetEncoderParameters);
      TargetEncoderModel targetEncoderModel = builder.trainModel().get();
      Scope.track_generic(targetEncoderModel);

      Frame transformedTrainWithModelFromBuilder = targetEncoderModel.transform(fr, DataLeakageHandlingStrategy.KFold,
              false, null, targetEncoderParameters._seed);
      Scope.track(transformedTrainWithModelFromBuilder);
      
      //Stage 2: 
      // Let's create encoding map by TargetEncoder directly and transform with it
      TargetEncoder tec = new TargetEncoder(new String[]{ "embarked", "home.dest"});

      teEncodingMap = tec.prepareEncodingMap(fr, responseColumnName, foldColumnName, false);
      Frame transformedTrainWithTargetEncoder = tec.applyTargetEncoding(fr, responseColumnName, teEncodingMap,
              DataLeakageHandlingStrategy.KFold, foldColumnName, targetEncoderParameters._blending, false, TargetEncoder.DEFAULT_BLENDING_PARAMS, targetEncoderParameters._seed);
      Scope.track(transformedTrainWithTargetEncoder);

      assertBitIdentical(transformedTrainWithModelFromBuilder, transformedTrainWithTargetEncoder);
    } finally {
      encodingMapCleanUp(teEncodingMap);
      Scope.exit();
    }
  }

  @Test
  public void columnOrderHasNoEffectWhenNoiseIsZero() {
    try {
      Scope.enter();
      final Frame frame = parse_test_file("./smalldata/gbm_test/titanic.csv");
      Scope.track(frame);
      asFactor(frame, "survived");
      addKFoldColumn(frame, "fold_column", 5, 1234L);
      RowIndexTask.addRowIndex(frame);
      DKV.put(frame);

      final TargetEncoder te1 = new TargetEncoder(new String[]{"home.dest", "embarked"});
      final Map<String, Frame> encodingMap1 = te1.prepareEncodingMap(frame, "survived", "fold_column", false);

      final TargetEncoder te2 = new TargetEncoder(new String[]{"embarked", "home.dest"});
      final Map<String, Frame> encodingMap2 = te2.prepareEncodingMap(frame, "survived", "fold_column", false);

      // check the encodings are actually the same
      areEncodingMapsIdentical(encodingMap1, encodingMap2);

      final Frame te1Result = te1.applyTargetEncoding(frame, "survived", encodingMap1,
              DataLeakageHandlingStrategy.KFold, "fold_column", false, 0, false,
              TargetEncoder.DEFAULT_BLENDING_PARAMS, 1234);
      Scope.track(te1Result);
      Frame te1ResultSorted = Scope.track(Merge.sort(te1Result, te1Result.find(RowIndexTask.ROW_INDEX_COL)));

      final Frame te2Result = te2.applyTargetEncoding(frame, "survived", encodingMap2,
              DataLeakageHandlingStrategy.KFold, "fold_column", false, 0, false,
              TargetEncoder.DEFAULT_BLENDING_PARAMS, 1234);
      Scope.track(te2Result);
      Frame te2ResultSorted = Scope.track(Merge.sort(te2Result, te2Result.find(RowIndexTask.ROW_INDEX_COL)));

      encodingMapCleanUp(encodingMap1);
      encodingMapCleanUp(encodingMap2);

      Frame te2ResultSortedReordered = new Frame(te1ResultSorted.names(), te2ResultSorted.vecs(te1ResultSorted.names()));
      assertBitIdentical(te1ResultSorted, te2ResultSortedReordered);
    } finally {
      Scope.exit();
    }
  }

  private static class RowIndexTask extends MRTask<RowIndexTask> {
    static String ROW_INDEX_COL = "__row_index";

    @Override
    public void map(Chunk c, NewChunk nc) {
      long start = c.start();
      for (int i = 0; i < c._len; i++) {
        nc.addNum(start + i);
      }
    }

    private static void addRowIndex(Frame f) {
      Vec indexVec = new RowIndexTask().doAll(Vec.T_NUM, f.anyVec())
              .outputFrame().anyVec();
      f.insertVec(0, ROW_INDEX_COL, indexVec);
    }
  }

  private void areEncodingMapsIdentical(Map<String, Frame> encodingMapFromTargetEncoder, Map<String, Frame> targetEncodingMapFromBuilder) {
    for (Map.Entry<String, Frame> entry : targetEncodingMapFromBuilder.entrySet()) {
      String teColumn = entry.getKey();
      Frame correspondingEncodingFrameFromTargetEncoder = encodingMapFromTargetEncoder.get(teColumn);
      assertBitIdentical(entry.getValue(), correspondingEncodingFrameFromTargetEncoder);
    }
  }
}
