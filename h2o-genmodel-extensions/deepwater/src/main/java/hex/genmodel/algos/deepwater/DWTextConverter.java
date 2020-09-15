package hex.genmodel.algos.deepwater;

import hex.genmodel.GenModel;
import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.DefaultRowToRawDataConverter;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.exception.PredictException;

import java.util.Map;

public class DWTextConverter extends DefaultRowToRawDataConverter<GenModel> {
  
  DWTextConverter(Map<String, Integer> modelColumnNameToIndexMap, Map<Integer, CategoricalEncoder> domainMap,
                  EasyPredictModelWrapper.ErrorConsumer errorConsumer, EasyPredictModelWrapper.Config config) {
    super(modelColumnNameToIndexMap, domainMap, errorConsumer, config);
  }

  @Override
  protected boolean convertValue(String columnName, Object o, CategoricalEncoder catEncoder, int targetIndex, double[] rawData) throws PredictException {
    if (o instanceof String) {
      throw new PredictException("MOJO scoring for text classification is not yet implemented.");
    }
    return super.convertValue(columnName, o, catEncoder, targetIndex, rawData);
  }

}
