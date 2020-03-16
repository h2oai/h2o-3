package hex.genmodel;

import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowToRawDataConverter;

import java.util.Map;

public interface RowtoRawDataConverterProvider {

    Class isFor();

    RowToRawDataConverter get(final GenModel genModel,
                              Map<String, Integer> modelColumnNameToIndexMap,
                              Map<Integer, CategoricalEncoder> domainMap,
                              EasyPredictModelWrapper.ErrorConsumer errorConsumer,
                              EasyPredictModelWrapper.Config config);

}
