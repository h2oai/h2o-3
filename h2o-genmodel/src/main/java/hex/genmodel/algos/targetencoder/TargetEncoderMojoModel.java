package hex.genmodel.algos.targetencoder;

import hex.genmodel.MojoModel;
import hex.genmodel.easy.RowData;

import java.util.Iterator;
import java.util.Map;

public class TargetEncoderMojoModel extends MojoModel implements FeatureTransformerModel{

  public TargetEncoderMojoModel(String[] columns, String[][] domains, String responseName) {
    super(columns, domains, responseName);
  }

  public Map<String, Map<String, int[]>> _targetEncodingMap;
  public boolean _withBlending;
  public double _inflectionPoint;
  public double _smoothing;
  private double _priorMean = -1; // Could be passed from the model training phase so that we don't have to recompute it here. At least we should compute it once per mojoModel

  @Override
  public RowData transform0(RowData data) {
    
    if(_targetEncodingMap != null) {
      for (Map.Entry<String, Map<String, int[]>> columnToEncodingsMap : _targetEncodingMap.entrySet()) {
        String columnName = columnToEncodingsMap.getKey();
        String originalValue = (String) data.get(columnName);
        Map<String, int[]> encodings = columnToEncodingsMap.getValue();

        _priorMean = _priorMean == -1 ? computePriorMean(encodings) : _priorMean;
        
        int[] correspondingNumAndDen = encodings.get(originalValue);

        double numberOfRowsInCurrentCategory = correspondingNumAndDen[1];
        double lambda = 1.0 / (1 + Math.exp((_inflectionPoint - numberOfRowsInCurrentCategory) / _smoothing));
        double posteriorMean = (double) correspondingNumAndDen[0] / correspondingNumAndDen[1];
        double blendedValue = lambda * posteriorMean + (1 - lambda) * _priorMean;
        
        data.put(columnName + "_te", blendedValue);
      }
    } else {
      throw new IllegalStateException("Encoding map is missing.");
    }
    return data;
  }

  /**
   * Computes prior mean i.e. unconditional mean of the response. Should be the same for all columns
   */
  double computePriorMean(Map<String, int[]> encodingMap) {
    int sumOfNumerators = 0;
    int sumOfDenominators = 0;
    Iterator<int[]> iterator = encodingMap.values().iterator();
    while( iterator.hasNext()) {
      int[] next = iterator.next();
      sumOfNumerators += next[0];
      sumOfDenominators += next[1];
    }
    return (double) sumOfNumerators / sumOfDenominators;
  }
  
  @Override
  public double[] score0(double[] row, double[] preds) {
    throw new UnsupportedOperationException("TargetEncoderMojoModel Model doesn't support scoring using score0() function");
  }

}
