package hex.genmodel.algos.targetencoder;

import hex.genmodel.MojoModel;

import java.util.*;

public class TargetEncoderMojoModel extends MojoModel {

  public TargetEncoderMojoModel(String[] columns, String[][] domains, String responseName) {
    super(columns, domains, responseName);
  }

  public EncodingMaps _targetEncodingMap;
  public Map<String, Integer> _teColumnNameToIdx = new HashMap<>();
  public boolean _withBlending;
  public double _inflectionPoint;
  public double _smoothing;

  // Could be passed from the model training phase so that we don't have to recompute it here. Or maybe it is fine as it should be computed only once per mojoModel
  private double _priorMean = -1;

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
    if(_targetEncodingMap != null) {
      
      int predictionIndex = 0;
      // Order of entries is not defined in sets(hash map). So we need some source of consistency.
      // Following will guarantee order of transformations. Ascending order based on index of te column in the input
      LinkedHashMap<String, EncodingMap> sortedByColumnIndex = sortByColumnIndex(_targetEncodingMap.encodingMap());

      for (Map.Entry<String, EncodingMap> columnToEncodingsMap : sortedByColumnIndex.entrySet() ) {
        
        String teColumn = columnToEncodingsMap.getKey();
        int indexOfColumnInRow = _teColumnNameToIdx.get(teColumn);
        
        int originalValue = Double.isNaN(row[indexOfColumnInRow]) ? -1 :  (int)row[indexOfColumnInRow]; // original categorical level represented as index
        EncodingMap encodings = columnToEncodingsMap.getValue();

        _priorMean = _priorMean == -1 ? computePriorMean(encodings) : _priorMean;

        int[] correspondingNumAndDen = encodings._encodingMap.get(originalValue);

        if(correspondingNumAndDen == null) {
          preds[predictionIndex] = _priorMean;
        }
        else {
          double posteriorMean = (double) correspondingNumAndDen[0] / correspondingNumAndDen[1];
          
          if(_withBlending) {
            int numberOfRowsInCurrentCategory = correspondingNumAndDen[1];
            double lambda = computeLambda(numberOfRowsInCurrentCategory, _inflectionPoint, _smoothing);
            double blendedValue = computeBlendedEncoding(lambda, posteriorMean, _priorMean);

            preds[predictionIndex] = blendedValue;
          }
          else {
            preds[predictionIndex] = posteriorMean;
          }
          
        }
        predictionIndex++;
      }
    } else {
      throw new IllegalStateException("Encoding map is missing.");
    }
    
    return preds;
  }

  public static class SortByKeyAssociatedIndex < K extends String, V > implements  Comparator<Map.Entry<K, V>>
  {
    public Map<String, Integer> _teColumnNameToIdx;
    
    public SortByKeyAssociatedIndex(Map<String, Integer> teColumnNameToIdx) {
      _teColumnNameToIdx = teColumnNameToIdx;
    }

    @Override
    public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
      String keyLeft = o1.getKey();
      String keyRight= o2.getKey();
      Integer keyLeftIdx = _teColumnNameToIdx.get(keyLeft);
      Integer keyRightIdx = _teColumnNameToIdx.get(keyRight);
      return keyLeftIdx.compareTo(keyRightIdx);
    }
  }

  //Note: We don't have IcedLinkedHashMap so that we can avoid this conversion
  <K, V > LinkedHashMap<K, V> sortByColumnIndex(Map<K, V> map) {
    ArrayList<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
    Collections.sort(list, new SortByKeyAssociatedIndex(_teColumnNameToIdx));

    LinkedHashMap<K, V> result = new LinkedHashMap<>();
    for (Map.Entry<K, V> entry : list) {
      result.put(entry.getKey(), entry.getValue());
    }

    return result;
  }
}
