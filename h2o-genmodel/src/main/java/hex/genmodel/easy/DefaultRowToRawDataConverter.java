package hex.genmodel.easy;

import hex.genmodel.GenModel;
import hex.genmodel.easy.EasyPredictModelWrapper.Config;
import hex.genmodel.easy.EasyPredictModelWrapper.ErrorConsumer;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.exception.PredictNumberFormatException;
import hex.genmodel.easy.exception.PredictUnknownCategoricalLevelException;
import hex.genmodel.easy.exception.PredictUnknownTypeException;

import java.io.Serializable;
import java.util.Map;

/**
 * This class is intended to transform a RowData instance - for which we want to get prediction to - into a raw array
 */
public class DefaultRowToRawDataConverter<M extends GenModel> implements RowToRawDataConverter, Serializable {

  private final Map<String, Integer> _columnToOffsetIdx;
  private final Map<Integer, CategoricalEncoder> _offsetToEncoder;
  private final ErrorConsumer _errorConsumer;

  private final boolean _convertUnknownCategoricalLevelsToNa;
  private final boolean _convertInvalidNumbersToNa;
  
  public DefaultRowToRawDataConverter(Map<String, Integer> columnToOffsetIdx,
                                      Map<Integer, CategoricalEncoder> offsetToEncoder,
                                      ErrorConsumer errorConsumer,
                                      Config config) {
    _columnToOffsetIdx = columnToOffsetIdx;
    _offsetToEncoder = offsetToEncoder;
    _errorConsumer = errorConsumer;
    _convertUnknownCategoricalLevelsToNa = config.getConvertUnknownCategoricalLevelsToNa();
    _convertInvalidNumbersToNa = config.getConvertInvalidNumbersToNa();
  }

  @Override
  public double[] convert(RowData data, double[] rawData) throws PredictException {  
    for (String dataColumnName : data.keySet()) {
      Integer index = _columnToOffsetIdx.get(dataColumnName);

      // Skip column names that are not known.
      // Skip the "response" column which should not be included in `rawData`
      if (index == null || index >= rawData.length) {
        continue;
      }

      Object o = data.get(dataColumnName);
      if (convertValue(dataColumnName, o, _offsetToEncoder.get(index), index, rawData)) {
        return rawData;
      }
    }
    return rawData;
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

  ErrorConsumer getErrorConsumer() {
    return _errorConsumer;
  }

}
