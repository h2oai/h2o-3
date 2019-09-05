package hex.genmodel.algos.targetencoder;

import hex.genmodel.MojoModel;

import java.util.*;

public class TargetEncoderMojoModel extends MojoModel {

  public TargetEncoderMojoModel(String[] columns, String[][] domains, String responseName) {
    super(columns, domains, responseName);
    assert columns[columns.length - 1].equals(responseName);

    _teColumnNameToIdx = new HashMap<>(columns.length - 1);
    for (int i = 0; i < columns.length - 1; i++) {
      _teColumnNameToIdx.put(columns[i], i);
    }
  }

  public EncodingMaps _targetEncodingMap;
  public Map<String, Integer> _teColumnNameToIdx = new HashMap<>();
  public Map<String, Integer> _teColumnNameToMissingValuesPresence;
  public boolean _withBlending;
  public double _inflectionPoint;
  public double _smoothing;
  public double _priorMean;

  /**
   * Whether during training of the model unknown categorical level was imputed with NA level. 
   * It will determine whether we are using posterior probability of NA level or prior probability when substitution didn't take place.
   * TODO Default value is hardcoded to `true` as we need to investigate PUBDEV-6704
   */
  private final boolean _imputationOfUnknownLevelsIsEnabled = true;

  public static double computeLambda(int nrows, double inflectionPoint, double smoothing) {
    return 1.0 / (1 + Math.exp((inflectionPoint - nrows) / smoothing));
  }
  
  public static double computeBlendedEncoding(double lambda, double posteriorMean, double priorMean) {
    return lambda * posteriorMean + (1 - lambda) * priorMean;
  }
  
  @Override
  public double[] score0(double[] row, double[] preds) {
    if(_targetEncodingMap != null) {
      
      int predictionIndex = 0;
      // Order of entries is not defined in sets(hash map). So we need some source of consistency.
      // Following will guarantee order of transformations. Ascending order based on index of te column in the input
      LinkedHashMap<String, EncodingMap> sortedByColumnIndex = sortByColumnIndex(_targetEncodingMap.encodingMap());

      for (Map.Entry<String, EncodingMap> columnToEncodingsMap : sortedByColumnIndex.entrySet() ) {
        EncodingMap encodings = columnToEncodingsMap.getValue();

        String teColumn = columnToEncodingsMap.getKey();
        int indexOfColumnInRow = _teColumnNameToIdx.get(teColumn);
        
        double categoricalLevelIndex = row[indexOfColumnInRow]; 
        
        if (Double.isNaN(categoricalLevelIndex)) {
          if(_imputationOfUnknownLevelsIsEnabled) {
            if(_teColumnNameToMissingValuesPresence.get(teColumn) == 1) {
              int indexOfNALevel = encodings._encodingMap.size() - 1;
              computeEncodings(preds, predictionIndex, encodings, indexOfNALevel);
            } else { // imputation was enabled but we didn't encounter missing values in training data so using `_priorMean`
              preds[predictionIndex] = _priorMean;
            }
          } else {
            preds[predictionIndex] = _priorMean;
          }
        } else {
          //It is assumed that categorical levels are only represented with int values
          int categoricalLevelIndexAsInt = (int) categoricalLevelIndex;
          computeEncodings(preds, predictionIndex, encodings, categoricalLevelIndexAsInt);

        }
        predictionIndex++;
      }
    } else {
      throw new IllegalStateException("Encoding map is missing.");
    }
    
    return preds;
  }

  private void computeEncodings(double[] preds, int predictionIndex, EncodingMap encodings, int originalValueAsInt) {
    int[] correspondingNumAndDen = encodings._encodingMap.get(originalValueAsInt);

    double posteriorMean = (double) correspondingNumAndDen[0] / correspondingNumAndDen[1];

    if (_withBlending) {
      int numberOfRowsInCurrentCategory = correspondingNumAndDen[1];
      double lambda = computeLambda(numberOfRowsInCurrentCategory, _inflectionPoint, _smoothing);
      double blendedValue = computeBlendedEncoding(lambda, posteriorMean, _priorMean);

      preds[predictionIndex] = blendedValue;
    } else {
      preds[predictionIndex] = posteriorMean;
    }
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
