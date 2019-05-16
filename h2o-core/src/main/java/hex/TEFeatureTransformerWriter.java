package hex;

import hex.genmodel.Writer;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.IcedHashMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Transformer writer that knows how to write TargetEncoder transformer
 */
public class TEFeatureTransformerWriter extends FeatureTransformerWriter<TargetEncoderTmpRepresentative> {
  
  @Override
  public String version() {
    return "1.00";
  }

  // TODO maybe later it makes sense to pass TransformerDescriptor instead of transformer itself. 
  public TEFeatureTransformerWriter(TargetEncoderTmpRepresentative transformer) {
    super(transformer);
  }

  @Override
  protected void writeTransformerData() throws IOException {
    writeTargetEncodingMap();
  }

  // TODO No need to convert two times `convertEncodingMap` and `preconvertTargetEncodingMap`. This is just for POC.
  public Map<String, Map<String, int[]>> convertEncodingMap(Map<String, Frame> em) {

    IcedHashMap<String, Map<String, Model.TEComponents>> emPrepared = preconvertTargetEncodingMap(em); 
    IcedHashMap<String, Map<String, int[]>> transformedEncodingMap = null;
    if(emPrepared != null) {
      transformedEncodingMap = new IcedHashMap<>();
      for (Map.Entry<String, Map<String, Model.TEComponents>> entry : emPrepared.entrySet()) {
        String columnName = entry.getKey();
        Map<String, Model.TEComponents> encodingsForParticularColumn = entry.getValue();
        Map<String, int[]> encodingsForColumnMap = new HashMap<>();
        for (Map.Entry<String, Model.TEComponents> kv : encodingsForParticularColumn.entrySet()) {
          encodingsForColumnMap.put(kv.getKey(), kv.getValue().getNumeratorAndDenominator());
        }
        transformedEncodingMap.put(columnName, encodingsForColumnMap);
      }
    }
    return transformedEncodingMap;
  }

  /**
   * Writes encoding map into the file line by line
   */
  private void writeTargetEncodingMap() throws IOException {
    Map<String, Frame> targetEncodingMap1 = transformer.getTargetEncodingMap();
    Map<String, Map<String, int[]>> targetEncodingMap = convertEncodingMap(targetEncodingMap1);
    if(targetEncodingMap != null) {
      startWritingTextFile("feature_engineering/target_encoding.ini");
      for (Map.Entry<String, Map<String, int[]>> columnEncodingsMap : targetEncodingMap.entrySet()) {
        writeln("[" + columnEncodingsMap.getKey() + "]");
        Map<String, int[]> encodings = columnEncodingsMap.getValue();
        for (Map.Entry<String, int[]> catLevelInfo : encodings.entrySet()) {
          int[] numAndDenom = catLevelInfo.getValue();
          writelnkv(catLevelInfo.getKey(), numAndDenom[0] + " " + numAndDenom[1]);
        }
      }
      finishWritingTextFile();
    }
  }

  public IcedHashMap<String, Map<String, Model.TEComponents>> preconvertTargetEncodingMap(Map<String, Frame> encodingMap) {
    IcedHashMap<String, Map<String, Model.TEComponents>> transformedEncodingMap = new IcedHashMap<>();
    Map<String, FrameToTETable> tasks = new HashMap<>();

    for (Map.Entry<String, Frame> entry : encodingMap.entrySet()) {

      Frame encodingsForParticularColumn = entry.getValue();
      FrameToTETable task = new FrameToTETable().dfork(encodingsForParticularColumn);

      tasks.put(entry.getKey(), task);
    }

    for (Map.Entry<String, FrameToTETable> taskEntry : tasks.entrySet()) {
      transformedEncodingMap.put(taskEntry.getKey(), taskEntry.getValue().getResult().table);
    }
    return transformedEncodingMap;
  }

  static class FrameToTETable extends MRTask<FrameToTETable> {
    IcedHashMap<String, Model.TEComponents> table = new IcedHashMap<>();

    public FrameToTETable() { }

    @Override
    public void map(Chunk[] cs) {
      Chunk categoricalChunk = cs[0];
      String[] domain = categoricalChunk.vec().domain();
      int numRowsInChunk = categoricalChunk._len;
      // Note: we don't store fold column as we need only to be able to give predictions for data which is not encoded yet. 
      // We need folds only for the case when we applying TE to the frame which we are going to train our model on. 
      // But this is done once and then we don't need them anymore.
      for (int i = 0; i < numRowsInChunk; i++) {
        int[] numeratorAndDenominator = new int[2];
        numeratorAndDenominator[0] = (int) cs[1].at8(i);
        numeratorAndDenominator[1] = (int) cs[2].at8(i);
        int factor = (int) categoricalChunk.at8(i);
        String factorName = domain[factor];
        table.put(factorName, new Model.TEComponents(numeratorAndDenominator));
      }
    }

    @Override
    public void reduce(FrameToTETable mrt) {
      table.putAll(mrt.table);
    }
  }

}
