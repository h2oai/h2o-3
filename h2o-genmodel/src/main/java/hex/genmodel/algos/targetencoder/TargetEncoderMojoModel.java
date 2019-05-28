package hex.genmodel.algos.targetencoder;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.word2vec.WordEmbeddingModel;
import hex.genmodel.easy.RowData;

import java.util.HashMap;
import java.util.Map;

public class TargetEncoderMojoModel extends MojoModel implements FeatureTransformerModel{

  public TargetEncoderMojoModel(String[] columns, String[][] domains, String responseName) {
    super(columns, domains, responseName);
  }

  public Map<String, Map<String, int[]>> _targetEncodingMap;

  @Override
  public RowData transform0(RowData data) {
    if(_targetEncodingMap != null) {
      for (Map.Entry<String, Map<String, int[]>> columnToEncodingsMap : _targetEncodingMap.entrySet()) {
        String columnName = columnToEncodingsMap.getKey();
        String originalValue = (String) data.get(columnName);
        Map<String, int[]> encodings = columnToEncodingsMap.getValue();
        int[] correspondingNumAndDen = encodings.get(originalValue);
        double calculatedFrequency = (double) correspondingNumAndDen[0] / correspondingNumAndDen[1];
        data.put(columnName + "_te", calculatedFrequency);
      }
    } else {
      throw new IllegalStateException("Encoding map is missing.");
    }
    return data;
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    throw new UnsupportedOperationException("TargetEncoderMojoModel Model doesn't support scoring using score0() function");
  }

}
