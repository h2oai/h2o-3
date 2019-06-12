package hex.genmodel.algos.targetencoder;

import hex.genmodel.MojoModel;
import hex.genmodel.easy.RowData;

import java.util.Iterator;
import java.util.Map;

public class TargetEncoderMojoModel extends MojoModel implements FeatureTransformerModel{

  public TargetEncoderMojoModel(String[] columns, String[][] domains, String responseName) {
    super(columns, domains, responseName);
  }

  public EncodingMaps _targetEncodingMap;
  public boolean _withBlending;
  public double _inflectionPoint;
  public double _smoothing;

  // Could be passed from the model training phase so that we don't have to recompute it here. Or maybe it is fine as it should be computed only once per mojoModel
  private double _priorMean = -1; 

  @Override
  public RowData transform(RowData data) {
    
    if(_targetEncodingMap != null) {
      for (Map.Entry<String, EncodingMap> columnToEncodingsMap : _targetEncodingMap.entrySet()) {
        String columnName = columnToEncodingsMap.getKey();
        String originalValue = (String) data.get(columnName);
        EncodingMap encodings = columnToEncodingsMap.getValue();

        _priorMean = _priorMean == -1 ? computePriorMean(encodings) : _priorMean;
        
        int[] correspondingNumAndDen = encodings._encodingMap.get(originalValue);

        int numberOfRowsInCurrentCategory = correspondingNumAndDen[1];
        double lambda = computeLambda(numberOfRowsInCurrentCategory, _inflectionPoint, _smoothing);
        double posteriorMean = (double) correspondingNumAndDen[0] / correspondingNumAndDen[1];
        double blendedValue = computeBlendedEncoding(lambda, posteriorMean, _priorMean);
        
        data.put(columnName + "_te", blendedValue);
      }
    } else {
      throw new IllegalStateException("Encoding map is missing.");
    }
    return data;
  }

  public static double computeLambda(int nrows, double inflectionPoint, double smoothing) {
    return 1.0 / (1 + Math.exp((inflectionPoint - nrows) / smoothing));
  }
  
  public static double computeBlendedEncoding(double lambda, double posteriorMean, double priorMean) {
    return lambda * posteriorMean + (1 - lambda) * priorMean;
  }
  
  /**
   * Computes prior mean i.e. unconditional mean of the response. Should be the same for all columns
   */
  public static double computePriorMean(EncodingMap encodingMap) {
    int sumOfNumerators = 0;
    int sumOfDenominators = 0;
    Iterator<int[]> iterator = encodingMap._encodingMap.values().iterator();
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
