package hex.genmodel.easy;

import hex.genmodel.GenModel;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.exception.PredictNumberFormatException;
import hex.genmodel.easy.exception.PredictUnknownCategoricalLevelException;
import hex.genmodel.easy.exception.PredictUnknownTypeException;

import java.io.Serializable;
import java.util.Map;

/**
 * This class is intended to transform a RowData instance - for which we want to get prediction to - into a raw array
 */
public class RowToRawDataConverter implements Serializable {

  private final Map<String, Integer> _modelColumnNameToIndexMap;
  private final Map<Integer, CategoricalEncoder> _domainMap;
  private final EasyPredictModelWrapper.ErrorConsumer _errorConsumer;

  private final boolean _convertUnknownCategoricalLevelsToNa;
  private final boolean _convertInvalidNumbersToNa;
  
  public RowToRawDataConverter(GenModel m,
                               Map<String, Integer> modelColumnNameToIndexMap,
                               Map<Integer, CategoricalEncoder> domainMap,
                               EasyPredictModelWrapper.ErrorConsumer errorConsumer,
                               EasyPredictModelWrapper.Config config) {
    _modelColumnNameToIndexMap = modelColumnNameToIndexMap;
    _domainMap = domainMap;
    _errorConsumer = errorConsumer;
    _convertUnknownCategoricalLevelsToNa = config.getConvertUnknownCategoricalLevelsToNa();
    _convertInvalidNumbersToNa = config.getConvertInvalidNumbersToNa();
  }

  /**
   * 
   * @param data instance of RowData we want to get prediction for
   * @param rawData array that will be filled up from RowData instance and returned
   * @return `rawData` array with data from RowData. 
   * @throws PredictException Note: name of the exception feels like out of scope of the class with name `RowToRawDataConverter` 
   * but this conversion is only needed to make it possible to produce predictions so it makes sense
   */
  public double[] convert(RowData data, double[] rawData) throws PredictException {  
    for (String dataColumnName : data.keySet()) {
      Integer index = _modelColumnNameToIndexMap.get(dataColumnName);

      // Skip column names that are not known.
      // Skip the "response" column which should not be included in `rawData`
      if (index == null || index >= rawData.length) {
        continue;
      }

      Object o = data.get(dataColumnName);
      if (convertValue(dataColumnName, o, _domainMap.get(index), index, rawData)) {
        return rawData;
      }
    }
    return rawData;
  }

  /**
   *
   * @param data instance of RowData we want to get prediction for
   * @param columnName name of a column for which we are extracting value for                
   * @return value for the specific column. 
   * @throws PredictException Note: name of the exception feels like out of scope of the class with name `RowToRawDataConverter` 
   * but this conversion is only needed to make it possible to produce predictions so it makes sense
   */
  public double convertValue(RowData data, String columnName) throws PredictException {
    Integer index = _modelColumnNameToIndexMap.get(columnName);

    // Skip column names that are not known.
    if (index == null) {
      return Double.NaN;
    }

    Object o = data.get(columnName);
    double[] outputRawData = new double[index+1];
    outputRawData[index] = Double.NaN;
    convertValue(columnName, o, _domainMap.get(index), index, outputRawData);

    return outputRawData[index];
  }

  protected boolean convertValue(String columnName, Object o, CategoricalEncoder catEncoder,
                                 int targetIndex, double[] rawData) throws PredictException {
    if (catEncoder == null) {
      // Column is either numeric or a string (for images or text)
      double value = Double.NaN;
      if (o instanceof String) {
        String s = ((String) o).trim();
        // numeric
        try {
          value = Double.parseDouble(s);
        } catch (NumberFormatException nfe) {
          if (!_convertInvalidNumbersToNa)
            throw new PredictNumberFormatException("Unable to parse value: " + s + ", from column: " + columnName + ", as Double; " + nfe.getMessage());
        }
      } else if (o instanceof Double) {
        value = (Double) o;
      } else {
        throw new PredictUnknownTypeException(
                "Unexpected object type " + o.getClass().getName() + " for numeric column " + columnName);
      }
      if (Double.isNaN(value)) {
        // If this point is reached, the original value remains NaN.
        _errorConsumer.dataTransformError(columnName, o, "Given non-categorical value is unparseable, treating as NaN.");
      }
      rawData[targetIndex] = value;
    } else {
      // Column has categorical value.
      if (o instanceof String) {
        String levelName = (String) o;
        if (! catEncoder.encodeCatValue(levelName, rawData)) {
          if (_convertUnknownCategoricalLevelsToNa) {
            catEncoder.encodeNA(rawData);
            _errorConsumer.unseenCategorical(columnName, o, "Previously unseen categorical level detected, marking as NaN.");
          } else {
            _errorConsumer.dataTransformError(columnName, o, "Unknown categorical level detected.");
            throw new PredictUnknownCategoricalLevelException("Unknown categorical level (" + columnName + "," + levelName + ")", columnName, levelName);
          }
        }
      } else if (o instanceof Double && Double.isNaN((double) o)) {
        _errorConsumer.dataTransformError(columnName, o, "Missing factor value detected, setting to NaN");
        catEncoder.encodeNA(rawData); // Missing factor is the only Double value allowed
      } else {
        _errorConsumer.dataTransformError(columnName, o, "Unknown categorical variable type.");
        throw new PredictUnknownTypeException(
                "Unexpected object type " + o.getClass().getName() + " for categorical column " + columnName);
      }
    }

    return false;
  }

  EasyPredictModelWrapper.ErrorConsumer getErrorConsumer() {
    return _errorConsumer;
  }

}
