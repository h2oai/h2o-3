package hex.genmodel;

import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowToRawDataConverter;

import java.util.Map;

public class CommonRowtoRawDataConverterProvider implements RowtoRawDataConverterProvider {
    @Override
    public Class<? extends GenModel> isFor() {
        return GenModel.class; // Common RawDataConverter for all GenModels.
    }

    @Override
    public RowToRawDataConverter get(final GenModel genModel,
                                     Map<String, Integer> modelColumnNameToIndexMap,
                                     Map<Integer, CategoricalEncoder> domainMap,
                                     EasyPredictModelWrapper.ErrorConsumer errorConsumer,
                                     EasyPredictModelWrapper.Config config) {
        return new RowToRawDataConverter(genModel, modelColumnNameToIndexMap, domainMap,
                errorConsumer, config);
    }
}
