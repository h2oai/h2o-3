package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderOutput;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import hex.ModelMojoWriter;
import water.fvec.Frame;
import water.fvec.Vec;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import static ai.h2o.targetencoding.TargetEncoderHelper.*;
import static hex.genmodel.algos.targetencoder.TargetEncoderMojoReader.ENCODING_MAP_PATH;
import static hex.genmodel.algos.targetencoder.TargetEncoderMojoReader.MISSING_VALUES_PRESENCE_MAP_PATH;

public class TargetEncoderMojoWriter extends ModelMojoWriter<TargetEncoderModel, TargetEncoderParameters, TargetEncoderOutput> {
    
  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public TargetEncoderMojoWriter() {
  }

  public TargetEncoderMojoWriter(TargetEncoderModel model) {
    super(model);
  }

  @Override
  public String mojoVersion() {
    return "1.00";
  }

  @Override
  protected void writeModelData() throws IOException {
    writeTargetEncodingInfo();
    writeTargetEncodingMap();
  }

  /**
   * Writes target encoding's extra info
   */
  private void writeTargetEncodingInfo() throws IOException {
    TargetEncoderOutput output = model._output;
    TargetEncoderParameters teParams = output._parms;
    writekv("keep_original_categorical_columns", teParams._keep_original_features);
    writekv("with_blending", teParams._blending);
    if (teParams._blending) {
      writekv("inflection_point", teParams._inflection_point);
      writekv("smoothing", teParams._smoothing);
    }

    List<String> nonPredictors =  Arrays.stream(new String[]{
            model._output.weightsName(),
            model._output.offsetName(),
            model._output.foldName(),
            model._output.responseName()
    }).filter(Objects::nonNull).collect(Collectors.toList());
    writekv("non_predictors", String.join(";", nonPredictors));

    //XXX: additional file unnecessary, we could just write the list/set of columns with NAs
    Map<String, Boolean> col2HasNAs = output._te_column_to_hasNAs;
    startWritingTextFile(MISSING_VALUES_PRESENCE_MAP_PATH);
    for(Entry<String, Boolean> entry: col2HasNAs.entrySet()) {
      writelnkv(entry.getKey(), entry.getValue() ? "1" : "0");
    }
    finishWritingTextFile();
  }

  /**
   * Writes encoding map into the file line by line
   */
  private void writeTargetEncodingMap() throws IOException {
    TargetEncoderOutput targetEncoderOutput = model._output;
    int nclasses = model._output._nclasses;
    Map<String, Frame> targetEncodingMap = targetEncoderOutput._target_encoding_map;

    groupEncodingsByFoldColumnIfNeeded(targetEncoderOutput, targetEncodingMap);

    startWritingTextFile(ENCODING_MAP_PATH);
    for (Entry<String, Frame> encodingsEntry : targetEncodingMap.entrySet()) {
      String column = encodingsEntry.getKey();
      Frame encodings = encodingsEntry.getValue();
      Vec.Reader catRead = encodings.vec(0).new Reader();
      Vec.Reader numRead = encodings.vec(NUMERATOR_COL).new Reader();
      Vec.Reader denRead = encodings.vec(DENOMINATOR_COL).new Reader();
      Vec.Reader tcRead = nclasses > 2 ? encodings.vec(TARGETCLASS_COL).new Reader() : null;
      
      writeln("[" + column + "]");
      for (int i=0; i<catRead.length(); i++) {
        String category = Long.toString(catRead.at8(i));
        String[] components = tcRead == null 
                ? new String[] {Double.toString(numRead.at(i)), Double.toString(denRead.at(i))}
                : new String[] {Double.toString(numRead.at(i)), Double.toString(denRead.at(i)), Long.toString(tcRead.at8(i))};
        writelnkv(category, String.join(" ", components));
      }
    }
    
    finishWritingTextFile();
  }

  /**
   * For transforming (making predictions) non-training data we don't need `te folds` in our encoding maps.
   */
  private void groupEncodingsByFoldColumnIfNeeded(TargetEncoderOutput targetEncoderOutput, Map<String, Frame> targetEncodingMap) {
    String foldColumn = targetEncoderOutput._parms._fold_column;
    if (foldColumn != null) {
      try {
        for (Entry<String, Frame> encodingMapEntry : targetEncodingMap.entrySet()) {
          String teColumn = encodingMapEntry.getKey();
          Frame encodingsWithFolds = encodingMapEntry.getValue();
          Frame encodingsWithoutFolds = groupEncodingsByCategory(encodingsWithFolds, encodingsWithFolds.find(teColumn) , true);
          targetEncodingMap.put(teColumn, encodingsWithoutFolds);
          encodingsWithFolds.delete();
        }
      } catch (Exception ex) {
        throw new IllegalStateException("Failed to group encoding maps by fold column", ex);
      }
    }
  }
  
}
