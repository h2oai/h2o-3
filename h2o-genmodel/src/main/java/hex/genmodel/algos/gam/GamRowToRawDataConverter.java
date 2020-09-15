package hex.genmodel.algos.gam;

import hex.genmodel.easy.*;
import hex.genmodel.easy.exception.PredictException;

import java.util.Map;

public class GamRowToRawDataConverter extends DefaultRowToRawDataConverter<GamMojoModelBase> {
  
  private final GamMojoModelBase _model;
  
  public GamRowToRawDataConverter(GamMojoModelBase m, 
                                  Map<String, Integer> modelColumnNameToIndexMap, 
                                  Map<Integer, CategoricalEncoder> domainMap, 
                                  EasyPredictModelWrapper.ErrorConsumer errorConsumer, 
                                  EasyPredictModelWrapper.Config config) {
    super(modelColumnNameToIndexMap, domainMap, errorConsumer, config);
    _model = m;
  }

  @Override
  public double[] convert(RowData data, double[] rawData) throws PredictException {
    rawData = super.convert(data, rawData);
    return _model.addExpandGamCols(rawData, data);
  }
}
