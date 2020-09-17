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

  public final Map<String, Integer> _columnNameToIdx;
  public EncodingMaps _targetEncodingMap;
  public Map<String, Boolean> _teColumn2HasNAs; // tells if a given encoded column has NAs
  public boolean _withBlending;
  public double _inflectionPoint;
  public double _smoothing;

  /**
   * Whether during training of the model unknown categorical level was imputed with NA level. 
   * It will determine whether we are using posterior probability of NA level or prior probability when substitution didn't take place.
   * TODO Default value is hardcoded to `true` as we need to investigate PUBDEV-6704
   */
  private final boolean _imputeUnknownLevels = true;

  public TargetEncoderMojoModel(String[] columns, String[][] domains, String responseName) {
    super(columns, domains, responseName);
    _columnNameToIdx = name2Idx(columns);
  }
  
  private Map<String, Integer> name2Idx(String[] columns) {
    Map<String, Integer> nameToIdx = new HashMap<>(columns.length);
    for (int i = 0; i < columns.length; i++) {
      nameToIdx.put(columns[i], i);
    }
    return nameToIdx;
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    if (_targetEncodingMap != null) {
      
      int predsIdx = 0;
      // Order of entries is not defined in sets(hash map). So we need some source of consistency.
      // Following will guarantee order of transformations. Ascending order based on index of te column in the input
      Map<String, EncodingMap> sortedByColumnIndex = sortByColumnIndex(_targetEncodingMap.encodingMap());

      for (Map.Entry<String, EncodingMap> columnToEncodings : sortedByColumnIndex.entrySet() ) {
        String teColumn = columnToEncodings.getKey();
        EncodingMap encodings = columnToEncodings.getValue();
        int colIdx = _columnNameToIdx.get(teColumn);
        double category = row[colIdx]; 
        
        int filled;
        if (Double.isNaN(category)) {
          if (_imputeUnknownLevels) {
            if (_teColumn2HasNAs.get(teColumn)) {
              filled = fillWithEncodedValues(preds, predsIdx, encodings, encodings.getNACategory());
            } else { // imputation was enabled but we didn't encounter missing values in training data so using `_priorMean`
              filled = fillWithPriorMean(preds, predsIdx, encodings);
            }
          } else {
            filled = fillWithPriorMean(preds, predsIdx, encodings);
          }
        } else {
          //It is assumed that categorical levels are only represented with int values
          filled = fillWithEncodedValues(preds, predsIdx, encodings, (int)category);
        }
        predsIdx += filled;
      }
    } else {
      throw new IllegalStateException("Encoding map is missing.");
    }
    
    return preds;
  }

  private int fillWithEncodedValues(double[] preds, int startIdx, EncodingMap encodings, int category) {
    if (_nclasses > 2) {
      for (int i=0; i<_nclasses-1; i++) {
        int targetClass = i+1; //for symmetry with binary, ignoring class 0
        double[] numDen = encodings.getNumDen(category, targetClass);  
        double priorMean = encodings.getPriorMean(targetClass);
        preds[startIdx+i] = computeEncodedValue(numDen, priorMean);
      }
      return _nclasses-1;
    } else {
      double[] numDen = encodings.getNumDen(category);
      double priorMean = encodings.getPriorMean();
      preds[startIdx] = computeEncodedValue(numDen, priorMean);
      return 1;
    }
  }
  
  private double computeEncodedValue(double[] numDen, double priorMean) {
    double posteriorMean = numDen[0] / numDen[1];
    if (_withBlending) {
      long nrows = (long)numDen[1];
      double lambda = computeLambda(nrows, _inflectionPoint, _smoothing);
      return computeBlendedEncoding(lambda, posteriorMean, priorMean);
    } else {
      return posteriorMean;
    }
  }
  
  private int fillWithPriorMean(double[] preds, int startIdx, EncodingMap encodings) {
    if (_nclasses > 2) {
      for (int i=0; i<_nclasses-1; i++) {
        preds[startIdx+i] = encodings.getPriorMean(i+1); //for symmetry with binary, ignoring class 0
      }
      return _nclasses-1;
    } else {
      preds[startIdx] = encodings.getPriorMean();
      return 1;
    }
  }

  Map<String, EncodingMap> sortByColumnIndex(final Map<String, EncodingMap> toSort) {
    Map<String, EncodingMap> sorted = new TreeMap<>(new Comparator<String>() {
      @Override
      public int compare(String lhs, String rhs) {
        return Integer.compare(_columnNameToIdx.get(lhs), _columnNameToIdx.get(rhs));
      }
    });
    sorted.putAll(toSort);
    return sorted;
  }
}
