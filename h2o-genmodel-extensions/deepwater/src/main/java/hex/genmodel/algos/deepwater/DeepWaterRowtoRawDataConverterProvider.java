package hex.genmodel.algos.deepwater;

import hex.genmodel.GenModel;
import hex.genmodel.RowtoRawDataConverterProvider;
import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowToRawDataConverter;

import java.util.Map;

public class DeepWaterRowtoRawDataConverterProvider implements RowtoRawDataConverterProvider {
    @Override
    public Class isFor() {
        return DeepwaterMojoModel.class;
    }

    @Override
    public RowToRawDataConverter get(GenModel genModel, Map<String, Integer> modelColumnNameToIndexMap, Map<Integer, CategoricalEncoder> domainMap, EasyPredictModelWrapper.ErrorConsumer errorConsumer, EasyPredictModelWrapper.Config config) {
        DeepwaterMojoModel deepwaterMojoModel = (DeepwaterMojoModel) genModel;
        if (deepwaterMojoModel._problem_type.equals("image"))
            return new DWImageConverter(deepwaterMojoModel, modelColumnNameToIndexMap, domainMap, errorConsumer, config);
        else if (deepwaterMojoModel._problem_type.equals("text")) {
            return new DWTextConverter(deepwaterMojoModel, modelColumnNameToIndexMap, domainMap, errorConsumer, config);
        }
        return new RowToRawDataConverter(deepwaterMojoModel, modelColumnNameToIndexMap, domainMap,
                errorConsumer, config);
    }
}
