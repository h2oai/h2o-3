package hex.genmodel;

import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowToRawDataConverter;

import java.util.Map;

public interface ConverterFactoryProvidingModel {

    /**
     * @return A new instance of {@link RowToRawDataConverter} related to the underlying {@link hex.genmodel.GenModel}
     */
    RowToRawDataConverter makeConverterFactory(Map<String, Integer> modelColumnNameToIndexMap,
                                               Map<Integer, CategoricalEncoder> domainMap,
                                               EasyPredictModelWrapper.ErrorConsumer errorConsumer,
                                               EasyPredictModelWrapper.Config config);
}
