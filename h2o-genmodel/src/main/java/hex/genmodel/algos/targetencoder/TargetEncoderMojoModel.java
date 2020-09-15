package hex.genmodel.algos.targetencoder;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoPreprocessor;

import java.util.*;

public class TargetEncoderMojoModel extends MojoModel implements MojoPreprocessor {

  public static double computeLambda(long nrows, double inflectionPoint, double smoothing) {
    return 1.0 / (1 + Math.exp((inflectionPoint - nrows) / smoothing));
  }

  public static double computeBlendedEncoding(double lambda, double posteriorMean, double priorMean) {
    return lambda * posteriorMean + (1 - lambda) * priorMean;
  }

  public final Map<String, Integer> _columnNameToIdx;
  public Map<String, Boolean> _teColumn2HasNAs; // tells if a given encoded column has NAs
  public boolean _withBlending;
  public double _inflectionPoint;
  public double _smoothing;

  List<String> _nonPredictors;
  Map<String, EncodingMap> _encodingsByCol;
  boolean _keepOriginalCategoricalColumns;
  
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
  
  static Map<String, Integer> name2Idx(String[] columns) {
    Map<String, Integer> nameToIdx = new HashMap<>(columns.length);
    for (int i = 0; i < columns.length; i++) {
      nameToIdx.put(columns[i], i);
    }
    return nameToIdx;
  }
  
  void setEncodings(EncodingMaps encodingMaps) {
    // Order of entries is not defined in sets(hash map). So we need some source of consistency.
    // Following will guarantee order of transformations. Ascending order based on index of te column in the input
    _encodingsByCol = sortByColumnIndex(encodingMaps);
  }

  @Override
  public int getPredsSize() {
    return _encodingsByCol.size() * getNumEncColsPerPredictor();
  }
  
  int getNumEncColsPerPredictor() {
    return nclasses() > 1
            ? (nclasses()-1)   // for classification we need to encode only n-1 classes
            : 1;               // for regression
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    if (_encodingsByCol!= null) {
      int predsIdx = 0;
      for (Map.Entry<String, EncodingMap> columnToEncodings : _encodingsByCol.entrySet() ) {
        String teColumn = columnToEncodings.getKey();
        EncodingMap encodings = columnToEncodings.getValue();
        int colIdx = _columnNameToIdx.get(teColumn);
        double category = row[colIdx]; 
        
        int filled;
        if (Double.isNaN(category)) {
          filled = encodeNA(preds, predsIdx, encodings, teColumn);
        } else {
          //It is assumed that categorical levels are only represented with int values
          filled = encodeCategory(preds, predsIdx, encodings, (int)category);
        }
        predsIdx += filled;
      }
    } else {
      throw new IllegalStateException("Encoding map is missing.");
    }
    
    return preds;
  }
  
  public EncodingMap getEncodings(String column) {
    return _encodingsByCol.get(column);
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
  
  int encodeCategory(double[] result, int startIdx, EncodingMap encodings, int category) {
    if (nclasses() > 2) {
      for (int i=0; i<nclasses()-1; i++) {
        int targetClass = i+1; //for symmetry with binary, ignoring class 0
        double[] numDen = encodings.getNumDen(category, targetClass);
        double priorMean = encodings.getPriorMean(targetClass);
        result[startIdx+i] = computeEncodedValue(numDen, priorMean);
      }
      return nclasses()-1;
    } else {
      double[] numDen = encodings.getNumDen(category);
      double priorMean = encodings.getPriorMean();
      result[startIdx] = computeEncodedValue(numDen, priorMean);
      return 1;
    }
  }
  
  int encodeNA(double[] result, int startIdx, EncodingMap encodings, String column) {
    int filled = 0; 
    if (_imputeUnknownLevels) {
      if (_teColumn2HasNAs.get(column)) {
        filled = encodeCategory(result, startIdx, encodings, encodings.getNACategory());
      } else { // imputation was enabled but we didn't encounter missing values in training data so using `_priorMean`
        filled = encodeWithPriorMean(result, startIdx, encodings);
      }
    } else {
      filled = encodeWithPriorMean(result, startIdx, encodings);
    }
    return filled;
  }

  private int encodeWithPriorMean(double[] preds, int startIdx, EncodingMap encodings) {
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

  Map<String, EncodingMap> sortByColumnIndex(final EncodingMaps encodingMaps) {
    Map<String, EncodingMap> sorted = new TreeMap<>(new Comparator<String>() {
      @Override
      public int compare(String lhs, String rhs) {
        return Integer.compare(_columnNameToIdx.get(lhs), _columnNameToIdx.get(rhs));
      }
    });
    sorted.putAll(encodingMaps.encodingMap());
    return sorted;
  }

  @Override
  public ModelProcessor makeProcessor(GenModel model) {
    return new TargetEncoderAsModelProcessor(this, model);
  }
  
}
