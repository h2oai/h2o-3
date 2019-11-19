package hex.genmodel.easy;

import hex.genmodel.GenModel;
import hex.genmodel.algos.deepwater.DeepwaterMojoModel;

import java.util.Map;

class RowDataConverterFactory {

  static RowToRawDataConverter makeConverter(GenModel m,
                                             Map<String, Integer> modelColumnNameToIndexMap,
                                             Map<Integer, CategoricalEncoder> domainMap,
                                             EasyPredictModelWrapper.ErrorConsumer errorConsumer,
                                             EasyPredictModelWrapper.Config config) {
    if (m instanceof DeepwaterMojoModel) {
      DeepwaterMojoModel dwm = (DeepwaterMojoModel) m;
      if (dwm._problem_type.equals("image"))
        return new DWImageConverter(dwm, modelColumnNameToIndexMap, domainMap, errorConsumer, config);
      else if (dwm._problem_type.equals("text")) {
        return new DWTextConverter(dwm, modelColumnNameToIndexMap, domainMap, errorConsumer, config);
      }
    }
    
    return new RowToRawDataConverter(m, modelColumnNameToIndexMap, domainMap,
            errorConsumer, config);
  }

}
