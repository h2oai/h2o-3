package hex.genmodel.easy;

import hex.genmodel.GenModel;
import hex.genmodel.easy.exception.PredictException;

import java.util.Map;

public class DWTextConverter extends RowToRawDataConverter {

  DWTextConverter(GenModel m, Map<String, Integer> modelColumnNameToIndexMap, Map<Integer, CategoricalEncoder> domainMap,
                  EasyPredictModelWrapper.ErrorConsumer errorConsumer, EasyPredictModelWrapper.Config config) {
    super(m, modelColumnNameToIndexMap, domainMap, errorConsumer, config);
  }

  @Override
  protected boolean convertValue(String columnName, Object o, CategoricalEncoder catEncoder, int targetIndex, double[] rawData) throws PredictException {
    if (o instanceof String) {
      throw new PredictException("MOJO scoring for text classification is not yet implemented.");
    }
    return super.convertValue(columnName, o, catEncoder, targetIndex, rawData);
  }

}
