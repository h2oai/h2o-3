package hex.genmodel;

import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowToRawDataConverter;

import java.util.Map;

public class CategoricalEncodingAsDataTransformer implements MojoTransformer.DataTransformer {

    private final GenModel _transformerModel;
    private final CategoricalEncoding _categoricalEncoding;
    private final MojoModel _model;
    
    public CategoricalEncodingAsDataTransformer(GenModel transformerModel, MojoModel model, CategoricalEncoding categoricalEncoding) {
        _transformerModel = transformerModel;
        _categoricalEncoding = categoricalEncoding;
        _model = model;
    }

    @Override
    public RowToRawDataConverter makeRowConverter(EasyPredictModelWrapper.ErrorConsumer errorConsumer, 
                                                  EasyPredictModelWrapper.Config config) {
        Map<String, Integer> columnToOffsetIdx = _categoricalEncoding.createColumnMapping(_transformerModel);
        Map<Integer, CategoricalEncoder> offsetToEncoder = _categoricalEncoding.createCategoricalEncoders(_transformerModel, columnToOffsetIdx);
        return _model.makeDefaultRowConverter(columnToOffsetIdx, offsetToEncoder, errorConsumer, config);
    }

    @Override
    public GenModel getTransformedModel() {
        return _model;
    }
}
