package hex.genmodel.algos.targetencoder;

import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoTransformer;

import java.util.*;

public class TargetEncoderMojoModel extends MojoModel implements MojoTransformer {

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
  public List<ColumnsToSingleMapping> _inencMapping; // maps input columns (or groups of columns) to the single column being effectively encoded (= key in _encodingsByCol).
  public List<ColumnsMapping> _inoutMapping;         // maps input columns (or groups of columns) to their corresponding fully encoded one(s).

  List<String> _origNames;
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
  
  protected void init() {
    if (_encodingsByCol == null) return;
    if (_inencMapping == null) _inencMapping = new ArrayList<>();
    if (_inoutMapping == null) _inoutMapping = new ArrayList<>();
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
      double category = computeCategoricalValue(colMap, row);
      String teColumn = colMap.toSingle();
      EncodingMap encodings = _encodingsByCol.get(teColumn);

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
   * @return an int representing the categorical value (as domain index) or Double.NaN.
   */
  double computeCategoricalValue(ColumnsToSingleMapping colMap, double[] row) {
    String[] colGroup = colMap.from();
    int[] colsIdx = columnsIndices(colGroup);
    double[] values = new double[colsIdx.length];
    for (int i=0; i<colsIdx.length; i++) {
      values[i] = row[colsIdx[i]];
    }
    if (values.length == 1) {
      return values[0];
    }
    assert colMap.toDomainAsNum() != null : "Missing domain for interaction between columns "+Arrays.toString(colGroup);
    long interaction = encodeInteraction(colsIdx, values);
    int catVal = Arrays.binarySearch(colMap.toDomainAsNum(), interaction);  //todo: verify that interactionDomain is always sorted
    return catVal < 0 ? Double.NaN : catVal;
  }
  
  String computeCategorical(ColumnsToSingleMapping colMap, Map<String, Object> row) {
    String[] colGroup = colMap.from();
    if (colGroup.length == 1) {
      return (String)row.get(colGroup[0]);
    }
    int[] colsIdx = columnsIndices(colGroup);
    double[] values = new double[colsIdx.length];
    for (int i=0; i<colsIdx.length; i++) {
      String colName = colGroup[i];
      Object colCat = row.get(colName);
      if (colCat == null) { //fast-track
        values[i] = Double.NaN;
      } else {
        assert colCat instanceof String : "categorical values for feature `"+colName+"` should be passed as String";
        String[] colDomain = getDomainValues(colName);
        int colValue = Arrays.asList(colDomain).indexOf((String) colCat); //todo: we can use Arrays.binarySearch if domain is always sorted. Verify!!
        values[i] = colValue < 0 ? Double.NaN : colValue;
      }
    }
    assert colMap.toDomainAsNum() != null : "Missing domain for interaction between columns "+Arrays.toString(colGroup);
    long interaction = encodeInteraction(colsIdx, values);
    return Long.toString(interaction);
  }

  /**
   * a condensed version of the encoding logic as implemented for the training phase in {@link ai.h2o.targetencoding.interaction.InteractionsEncoder}
   */
  private long encodeInteraction(int[] colsIdx, double[] colsValues) {
    // computing interaction value (see InteractionsEncoder)
    long interaction = 0;
    long multiplier = 1;
    for (int i=0; i<colsIdx.length; i++) {
      int colIdx = colsIdx[i];
      double val = colsValues[i];
      int domainCard = getDomainValues(colIdx).length;
      if (Double.isNaN(val) || val >= domainCard) val = domainCard;
      interaction += multiplier * val;
      multiplier *= (domainCard + 1);
    }
    return interaction;
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

  @Override
  public DataTransformer makeDataTransformer(GenModel model) {
    return new TargetEncoderAsModelProcessor(this, model);
  }
  
}
