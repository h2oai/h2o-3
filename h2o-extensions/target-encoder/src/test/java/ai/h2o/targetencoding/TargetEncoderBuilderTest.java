package ai.h2o.targetencoding;

import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Map;

import static ai.h2o.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.assertTrue;

public class TargetEncoderBuilderTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  @Test
  public void te_encoder_consistency_check(){

    Map<String, Frame> encodingMapFromTargetEncoder = null;
    Map<String, Frame> targetEncodingMapFromBuilder = null;

    Frame reference = null;
    for( int attempt = 0; attempt < 10; attempt++) {


      Scope.enter();
      try {
        Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
        String foldColumnName = "fold_column";
        String responseColumnName = "survived";
        asFactor(fr, responseColumnName);
        addKFoldColumn(fr, foldColumnName, 5, 1234L);
        Scope.track(fr);

        TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
        targetEncoderParameters._blending = false;
        targetEncoderParameters._response_column = responseColumnName;
        targetEncoderParameters._fold_column = foldColumnName;
        targetEncoderParameters._seed = 1234;
        targetEncoderParameters._ignored_columns = ignoredColumns(fr, "home.dest", "embarked", targetEncoderParameters._response_column,
                targetEncoderParameters._fold_column);
        targetEncoderParameters._train = fr._key;

        TargetEncoder.DataLeakageHandlingStrategy strategy = TargetEncoder.DataLeakageHandlingStrategy.KFold;
        TargetEncoder tec = new TargetEncoder(new String[]{ "embarked", "home.dest"});

        encodingMapFromTargetEncoder = tec.prepareEncodingMap(fr, responseColumnName, foldColumnName, false);

        printOutFrameAsTable(encodingMapFromTargetEncoder.get("home.dest"));

        Frame transformedTrainWithTargetEncoder = tec.applyTargetEncoding(fr, responseColumnName, encodingMapFromTargetEncoder,
                strategy, foldColumnName, targetEncoderParameters._blending, false, TargetEncoder.DEFAULT_BLENDING_PARAMS, targetEncoderParameters._seed);

        Scope.track(transformedTrainWithTargetEncoder);

        if(reference == null) {
          reference = transformedTrainWithTargetEncoder;
        } else {
          Log.info("Checking against reference");
          assertTrue("Encodings should be consistent across all attempts. Attempt number " + attempt,
                  isBitIdentical(reference, transformedTrainWithTargetEncoder));
        }

      } finally {
        removeEncodingMaps(encodingMapFromTargetEncoder, targetEncodingMapFromBuilder);
        Key[] keep = new Key[0];
        if (reference != null) {
          keep = ArrayUtils.append(reference.keys(), reference._key);
        }
        Scope.exit(keep);
      }

    }

    if (reference != null) {
      reference.remove();
    }
  }

  @Test
  public void te_builder_consistency_check() {

    Map<String, Frame> encodingMapFromTargetEncoder = null;
    Map<String, Frame> targetEncodingMapFromBuilder = null;
    TargetEncoderModel targetEncoderModel = null;

    Frame reference = null;
    for (int attempt = 0; attempt < 10; attempt++) {

      Scope.enter();
      try {
        Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
        String foldColumnName = "fold_column";

        addKFoldColumn(fr, foldColumnName, 5, 1234L);

        Scope.track(fr);
        String responseColumnName = "survived";

        asFactor(fr, responseColumnName);

        TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = new TargetEncoderModel.TargetEncoderParameters();
        targetEncoderParameters._blending = false;
        targetEncoderParameters._response_column = responseColumnName;
        targetEncoderParameters._fold_column = foldColumnName;
        targetEncoderParameters._seed = 1234;
        targetEncoderParameters._ignored_columns = ignoredColumns(fr, "home.dest", "embarked", targetEncoderParameters._response_column,
                targetEncoderParameters._fold_column);
        targetEncoderParameters._train = fr._key;

        TargetEncoderBuilder builder = new TargetEncoderBuilder(targetEncoderParameters);
        targetEncoderModel = builder.trainModel().get();

        TargetEncoder.DataLeakageHandlingStrategy strategy = TargetEncoder.DataLeakageHandlingStrategy.KFold;
        Frame transformedTrainWithModelFromBuilder = targetEncoderModel.transform(fr, TargetEncoder.DataLeakageHandlingStrategy.KFold.getVal(),
                false, null, targetEncoderParameters._seed);
        Scope.track(transformedTrainWithModelFromBuilder);
        targetEncodingMapFromBuilder = targetEncoderModel._output._target_encoding_map;
        printOutFrameAsTable(targetEncodingMapFromBuilder.get("home.dest"));

        if (reference == null) {
          reference = transformedTrainWithModelFromBuilder;
        } else {
          assertTrue("Encodings should be consistent across all attempts. Attempt number " + attempt,
                  isBitIdentical(reference, transformedTrainWithModelFromBuilder));
        }

      } finally {
        removeEncodingMaps(encodingMapFromTargetEncoder, targetEncodingMapFromBuilder);
        if (targetEncoderModel != null) {
          targetEncoderModel.remove();
        }
        Key[] keep = new Key[0];
        if (reference != null) {
          keep = ArrayUtils.append(reference.keys(), reference._key);
        }
        Scope.exit(keep);
      }

    }

    if (reference != null) {
      reference.remove();
    }
  }


  private void removeEncodingMaps(Map<String, Frame> encodingMapFromTargetEncoder, Map<String, Frame> targetEncodingMapFromBuilder) {
    if (encodingMapFromTargetEncoder != null)
      TargetEncoderFrameHelper.encodingMapCleanUp(encodingMapFromTargetEncoder);
    if (targetEncodingMapFromBuilder != null)
      TargetEncoderFrameHelper.encodingMapCleanUp(targetEncodingMapFromBuilder);
  }

}
