package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderOutput;
import hex.Model;
import hex.ModelMojoWriter;
import hex.genmodel.algos.targetencoder.EncodingMap;
import hex.genmodel.algos.targetencoder.EncodingMaps;
import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.IcedHashMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TargetEncoderMojoWriter extends ModelMojoWriter {

  @SuppressWarnings("unused")  // Called through reflection in ModelBuildersHandler
  public TargetEncoderMojoWriter() {
  }

  public TargetEncoderMojoWriter(Model model) {
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

  @Override
  protected void writeExtraInfo() {
    // Do nothing
  }

  /**
   * Writes target encoding's extra info
   */
  private void writeTargetEncodingInfo() throws IOException {
    TargetEncoderOutput output = ((TargetEncoderModel) model)._output;
    TargetEncoderModel.TargetEncoderParameters teParams = output._parms;
    writekv("with_blending", teParams._blending);
    if (teParams._blending) {
      writekv("inflection_point", teParams._inflection_point);
      writekv("smoothing", teParams._smoothing);
    }
    writekv("priorMean", output._prior_mean);
    
    Map<String, Boolean> col2HasNAs = output._te_column_to_hasNAs;
    startWritingTextFile("feature_engineering/target_encoding/te_column_name_to_missing_values_presence.ini");
    for(Map.Entry<String, Boolean> entry: col2HasNAs.entrySet()) {
      writelnkv(entry.getKey(), entry.getValue() ? "1" : "0");
    }
    finishWritingTextFile();
  }

  /**
   * Writes encoding map into the file line by line
   */
  private void writeTargetEncodingMap() throws IOException {
    TargetEncoderOutput targetEncoderOutput = ((TargetEncoderModel) model)._output;
    Map<String, Frame> targetEncodingMap = targetEncoderOutput._target_encoding_map;

    groupEncodingsByFoldColumnIfNeeded(targetEncoderOutput, targetEncodingMap);

    // We need to convert map only here - before writing to MOJO. Everywhere else having encoding maps based on Frames is fine.
    EncodingMaps convertedEncodingMap = convertEncodingMapValues(targetEncodingMap);
    startWritingTextFile("feature_engineering/target_encoding/encoding_map.ini");
    for (Map.Entry<String, EncodingMap> columnEncodingsMap : convertedEncodingMap.entrySet()) {
      writeln("[" + columnEncodingsMap.getKey() + "]");
      EncodingMap encodings = columnEncodingsMap.getValue();
      for (Map.Entry<Integer, double[]> catLevelInfo : encodings.entrySet()) {
        double[] numDen = catLevelInfo.getValue();
        writelnkv(catLevelInfo.getKey().toString(), numDen[0] + " " + numDen[1]);
      }
    }
    finishWritingTextFile();
  }

  /**
   * For transforming (making predictions) non-training data we don't need `te folds` in our encoding maps.
   * FIXME: can be removed as soon as grouping is already done in the training part when applying TE.
   *        Btw, this is in total contradiction with the idea of exposing leakage strategy in `transform` method.
   */
  private void groupEncodingsByFoldColumnIfNeeded(TargetEncoderOutput targetEncoderOutput, Map<String, Frame> targetEncodingMap) {
    String foldColumn = targetEncoderOutput._parms._fold_column;
    if (foldColumn != null) {
      try {
        for (Map.Entry<String, Frame> encodingMapEntry : targetEncodingMap.entrySet()) {
          String teColumn = encodingMapEntry.getKey();
          Frame encodingsWithFolds = encodingMapEntry.getValue();
          Frame encodingsWithoutFolds = TargetEncoderHelper.groupEncodingsByCategory(encodingsWithFolds, encodingsWithFolds.find(teColumn) , true);
          targetEncodingMap.put(teColumn, encodingsWithoutFolds);
          encodingsWithFolds.delete();
        }
      } catch (Exception ex) {
        throw new IllegalStateException("Failed to group encoding maps by fold column", ex);
      }
    }
  }
  
  static EncodingMaps convertEncodingMapValues(Map<String, Frame> encodingMap) {
    EncodingMaps convertedEncodingMap = new EncodingMaps();
    Map<String, FrameToTETableTask> tasks = new HashMap<>();

    for (Map.Entry<String, Frame> entry : encodingMap.entrySet()) {
      Frame encodingsForParticularColumn = entry.getValue();
      FrameToTETableTask task = new FrameToTETableTask().dfork(encodingsForParticularColumn);
      tasks.put(entry.getKey(), task);
    }

    for (Map.Entry<String, FrameToTETableTask> taskEntry : tasks.entrySet()) {
      FrameToTETableTask taskEntryValue = taskEntry.getValue();
      IcedHashMap<String, EncodingsComponents> table = taskEntryValue.getResult()._table;
      convertEncodingMapToGenModelFormat(convertedEncodingMap, taskEntry.getKey(), table);
      Scope.track(taskEntryValue._fr);
    }

    return convertedEncodingMap;
  }

  /**
   * Note: We can't use the same class for {numerator, denominator} in both `h2o-genmodel` and `h2o-automl` as we need it to be extended 
   * from Iced in `h2o-automl` to make it serializable to distribute MRTasks and we can't use this Iced class from `h2o-genmodel` module 
   * as there is no dependency between modules in this direction 
   *
   * @param convertedEncodingMap the Map we will put our converted encodings into
   * @param encodingMap encoding map for `teColumn`
   */
  private static void convertEncodingMapToGenModelFormat(EncodingMaps convertedEncodingMap, String teColumn, IcedHashMap<String, EncodingsComponents> encodingMap) {
    Map<Integer, double[]> tableGenModelFormat = new HashMap<>();
    for (Map.Entry<String, EncodingsComponents> entry : encodingMap.entrySet()) {
      EncodingsComponents value = entry.getValue();
      tableGenModelFormat.put(Integer.parseInt(entry.getKey()), new double[] {value.getNumerator(), value.getDenominator()});
    }
    convertedEncodingMap.put(teColumn, new EncodingMap(tableGenModelFormat));
  }


  /**
   * This task extracts the estimates from a TE frame, and stores them into a Map keyed by the categorical value.
   * A TE frame is just a frame with 3 or 4 columns: [categorical, fold (optional), numerator, denominator], each value from the category column being unique .
   */
  private static class FrameToTETableTask extends MRTask<FrameToTETableTask> {

    // IcedHashMap does not support integer keys so we will store indices as strings.
    public IcedHashMap<String, EncodingsComponents> _table = new IcedHashMap<>();


    public FrameToTETableTask() { }

    @Override
    public void map(Chunk[] cs) {
      Chunk categoricalChunk = cs[0];
      int numRowsInChunk = categoricalChunk._len;
      // Note: we don't store fold column as we need only to be able to give predictions for data which is not encoded yet. 
      // We need folds only for the case when we applying TE to the frame which we are going to train our model on. 
      // But this is done once and then we don't need them anymore.
      for (int i = 0; i < numRowsInChunk; i++) {
        double num = cs[1].atd(i);
        long den = cs[2].at8(i);
        int factor = (int) categoricalChunk.at8(i);
        _table.put(Integer.toString(factor), new EncodingsComponents(num, den));
      }
    }

    @Override
    public void reduce(FrameToTETableTask mrt) {
      _table.putAll(mrt._table);
    }
  }

}
