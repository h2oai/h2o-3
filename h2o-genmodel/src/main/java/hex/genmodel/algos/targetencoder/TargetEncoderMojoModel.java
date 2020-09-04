package hex.genmodel.algos.targetencoder;

import hex.genmodel.MojoModel;

import java.util.*;

public class TargetEncoderMojoModel extends MojoModel {

  public static double computeLambda(long nrows, double inflectionPoint, double smoothing) {
    return 1.0 / (1 + Math.exp((inflectionPoint - nrows) / smoothing));
  }

  public static double computeBlendedEncoding(double lambda, double posteriorMean, double priorMean) {
    return lambda * posteriorMean + (1 - lambda) * priorMean;
  }

  public EncodingMaps _targetEncodingMap;
  public Map<String, Integer> _columnNameToIdx;
  public Map<String, Boolean> _teColumn2HasNAs; // tells if a given encoded column has NAs
  public boolean _withBlending;
  public double _inflectionPoint;
  public double _smoothing;
  public double _priorMean;

  /**
   * Whether during training of the model unknown categorical level was imputed with NA level. 
   * It will determine whether we are using posterior probability of NA level or prior probability when substitution didn't take place.
   * TODO Default value is hardcoded to `true` as we need to investigate PUBDEV-6704
   */
  private final boolean _imputeUnknownLevels = true;

  public TargetEncoderMojoModel(String[] columns, String[][] domains, String responseName) {
    super(columns, domains, responseName);

    _columnNameToIdx = new HashMap<>(columns.length);
    for (int i = 0; i < columns.length - 1; i++) {
      _columnNameToIdx.put(columns[i], i);
    }
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    if (_targetEncodingMap != null) {
      
      int predsIdx = 0;
      // Order of entries is not defined in sets(hash map). So we need some source of consistency.
      // Following will guarantee order of transformations. Ascending order based on index of te column in the input
      Map<String, EncodingMap> sortedByColumnIndex = sortByColumnIndex(_targetEncodingMap.encodingMap());

      for (Map.Entry<String, EncodingMap> columnToEncodingsMap : sortedByColumnIndex.entrySet() ) {
        EncodingMap encodings = columnToEncodingsMap.getValue();

        String teColumn = columnToEncodingsMap.getKey();
        int colIdx = _columnNameToIdx.get(teColumn);
        
        double categoricalLevel = row[colIdx]; 
        
        if (Double.isNaN(categoricalLevel)) {
          if (_imputeUnknownLevels) {
            if (_teColumn2HasNAs.get(teColumn)) {
              int naLevel = encodings._encodingMap.size() - 1;
              computeEncodings(preds, predsIdx, encodings, naLevel);
            } else { // imputation was enabled but we didn't encounter missing values in training data so using `_priorMean`
              preds[predsIdx] = _priorMean;
            }
          } else {
            preds[predsIdx] = _priorMean;
          }
        } else {
          //It is assumed that categorical levels are only represented with int values
          computeEncodings(preds, predsIdx, encodings, (int)categoricalLevel);
        }
        predsIdx++;
      }
    } else {
      throw new IllegalStateException("Encoding map is missing.");
    }
    
    return preds;
  }

  private void computeEncodings(double[] preds, int idx, EncodingMap encodings, int categoryLevel) {
    double[] numDen = encodings._encodingMap.get(categoryLevel);
    double posteriorMean = numDen[0] / numDen[1];

    if (_withBlending) {
      long rowsCountWithCategory = (long)numDen[1];
      double lambda = computeLambda(rowsCountWithCategory, _inflectionPoint, _smoothing);
      double blendedValue = computeBlendedEncoding(lambda, posteriorMean, _priorMean);
      preds[idx] = blendedValue;
    } else {
      preds[idx] = posteriorMean;
    }
  }

  private static class SortByKeyAssociatedIndex < K extends String, V > implements  Comparator<Map.Entry<K, V>>
  {
    private final Map<String, Integer> _teColumnNameToIdx;
    
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

  <K, V > Map<K, V> sortByColumnIndex(Map<K, V> map) {
    ArrayList<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
    Collections.sort(list, new SortByKeyAssociatedIndex(_columnNameToIdx));

    Map<K, V> result = new LinkedHashMap<>();
    for (Map.Entry<K, V> entry : list) {
      result.put(entry.getKey(), entry.getValue());
    }

    return result;
  }
}
