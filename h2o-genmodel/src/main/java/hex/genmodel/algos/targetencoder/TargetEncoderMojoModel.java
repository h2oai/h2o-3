package hex.genmodel.algos.targetencoder;

import hex.genmodel.MojoModel;

import java.io.Serializable;
import java.util.*;

public class TargetEncoderMojoModel extends MojoModel {

  public static double computeLambda(long nrows, double inflectionPoint, double smoothing) {
    return 1.0 / (1 + Math.exp((inflectionPoint - nrows) / smoothing));
  }

  public static double computeBlendedEncoding(double lambda, double posteriorMean, double priorMean) {
    return lambda * posteriorMean + (1 - lambda) * priorMean;
  }
  
  static Map<String, Integer> name2Idx(String[] columns) {
    Map<String, Integer> nameToIdx = new HashMap<>(columns.length);
    for (int i = 0; i < columns.length; i++) {
      nameToIdx.put(columns[i], i);
    }
    return nameToIdx;
  }


  public final Map<String, Integer> _columnNameToIdx;
  public Map<String, Boolean> _teColumn2HasNAs; // tells if a given encoded column has NAs
  public boolean _withBlending;
  public double _inflectionPoint;
  public double _smoothing;

  List<String> _nonPredictors;
  Map<String, EncodingMap> _encodingsByCol;
  List<ColumnsToSingleMapping> _inencMapping;
  List<ColumnsMapping> _inoutMapping;
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
  
  void init() {
    if (_inencMapping.isEmpty() && _inoutMapping.isEmpty()) { // backwards compatibility for old mojos
      for (String col : _encodingsByCol.keySet()) {
        String[] in = new String[]{col};
//        String[] domain = getDomainValues(col);
        _inencMapping.add(new ColumnsToSingleMapping(in, col, null));
        String[] out = new String[getNumEncColsPerPredictor()];
        if (out.length > 1) {
          for (int i = 0; i < out.length; i++) {
            out[i] = col+"_"+(i+1)+"_te";  // better than nothing: (i+1) is the categorical value of the matching target
          }
        } else {
          out[0] = col+"_te";
        }
        _inoutMapping.add(new ColumnsMapping(in, out));
      }
    }
    
  }
  
  protected void setEncodings(EncodingMaps encodingMaps) {
    _encodingsByCol = encodingMaps.encodingMap();
  }

  @Override
  public int getPredsSize() {
    return _encodingsByCol == null ? 0 : _encodingsByCol.size() * getNumEncColsPerPredictor();
  }
  
  int getNumEncColsPerPredictor() {
    return nclasses() > 1
            ? (nclasses()-1)   // for classification we need to encode only n-1 classes
            : 1;               // for regression
  }

  @Override
  public double[] score0(double[] row, double[] preds) {
    if (_encodingsByCol == null) throw new IllegalStateException("Encoding map is missing.");
      
    int predsIdx = 0;
    for (ColumnsToSingleMapping colMap : _inencMapping) {
      String[] colGroup = colMap.from();
      String teColumn = colMap.toSingle();
      EncodingMap encodings = _encodingsByCol.get(teColumn);
      int[] colsIdx = columnsIndices(colGroup);
      
      double category;
      if (colsIdx.length == 1) {
        category = row[colsIdx[0]];
      } else {
        assert colMap.toDomainAsNum() != null : "Missing domain for interaction between columns "+Arrays.toString(colGroup);  
        category = interactionValue(row, colsIdx, colMap.toDomainAsNum());
      }
      
      int filled;
      if (Double.isNaN(category)) {
        filled = encodeNA(preds, predsIdx, encodings, teColumn);
      } else {
        //It is assumed that categorical levels are only represented with int values
        filled = encodeCategory(preds, predsIdx, encodings, (int)category);
      }
      predsIdx += filled;
    }
    
    return preds;
  }
  
  public EncodingMap getEncodings(String column) {
    return _encodingsByCol.get(column);
  } 
  
  private int[] columnsIndices(String[] names) {
    int[] indices = new int[names.length];
    for (int i=0; i < indices.length; i++) {
      indices[i] = _columnNameToIdx.get(names[i]);
    }
    return indices;
  }

  /**
   * a condensed version of the encoding logic as implemented for the training phase in {@link ai.h2o.targetencoding.InteractionsEncoder} 
   */
  private double interactionValue(double[] row, int[] colsIdx, long[] interactionDomain) {
    // computing interaction value (see InteractionsEncoder)
    long interaction = 0;
    long multiplier = 1;
    for (int colIdx : colsIdx) {
      double val = row[colIdx];
      int domainCard = getDomainValues(colIdx).length;
      if (Double.isNaN(val) || val >= domainCard) val = domainCard;
      interaction += multiplier * val;
      multiplier *= (domainCard + 1);
    }
    int catVal = Arrays.binarySearch(interactionDomain, interaction);
    return catVal < 0 ? Double.NaN : catVal;
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

}
