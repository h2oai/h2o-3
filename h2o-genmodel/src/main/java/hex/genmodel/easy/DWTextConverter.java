package hex.genmodel.easy;

import hex.genmodel.GenModel;
import hex.genmodel.easy.exception.PredictException;

import java.util.HashMap;

public class DWTextConverter extends RowToRawDataConverter {

  DWTextConverter(GenModel m, HashMap<String, Integer> modelColumnNameToIndexMap, HashMap<Integer, HashMap<String, Integer>> domainMap,
                         EasyPredictModelWrapper.ErrorConsumer errorConsumer, EasyPredictModelWrapper.Config config) {
    super(m, modelColumnNameToIndexMap, domainMap, errorConsumer, config);
  }

  @Override
  protected boolean convertValue(String columnName, Object o, String[] domainValues, int targetIndex, double[] rawData) throws PredictException {
    if (o instanceof String) {
      throw new PredictException("MOJO scoring for text classification is not yet implemented.");
    }
    return super.convertValue(columnName, o, domainValues, targetIndex, rawData);
  }

}
