package hex.genmodel;

import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowToRawDataConverter;

import java.util.Map;

public class CategoricalEncodingAsModelProcessor implements MojoPreprocessor.ModelProcessor {

    private final GenModel _preprocessedModel;
    private final CategoricalEncoding _categoricalEncoding;
    private final MojoModel _model;
    
    public CategoricalEncodingAsModelProcessor(GenModel preprocessedModel, MojoModel model, CategoricalEncoding categoricalEncoding) {
        _preprocessedModel = preprocessedModel;
        _categoricalEncoding = categoricalEncoding;
        _model = model;
    }

    @Override
    public RowToRawDataConverter makeRowConverter(EasyPredictModelWrapper.ErrorConsumer errorConsumer, 
                                                  EasyPredictModelWrapper.Config config) {
        Map<String, Integer> columnToOffsetIdx = _categoricalEncoding.createColumnMapping(_preprocessedModel);
        Map<Integer, CategoricalEncoder> offsetToEncoder = _categoricalEncoding.createCategoricalEncoders(_preprocessedModel, columnToOffsetIdx);
        return _model.makeDefaultRowConverter(columnToOffsetIdx, offsetToEncoder, errorConsumer, config);
    }

    @Override
    public GenModel getProcessedModel() {
        return _model;
    }
}
