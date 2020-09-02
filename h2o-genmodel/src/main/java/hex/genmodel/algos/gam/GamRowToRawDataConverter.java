package hex.genmodel.algos.gam;

import hex.genmodel.GenModel;
import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.RowToRawDataConverter;
import hex.genmodel.easy.exception.PredictException;

import java.util.Map;

public class GamRowToRawDataConverter extends RowToRawDataConverter {
  GamMojoModelBase _m;
  public GamRowToRawDataConverter(GenModel m, Map<String, Integer> modelColumnNameToIndexMap, Map<Integer, CategoricalEncoder> domainMap, EasyPredictModelWrapper.ErrorConsumer errorConsumer, EasyPredictModelWrapper.Config config) {
    super(m, modelColumnNameToIndexMap, domainMap, errorConsumer, config);
    _m = (GamMojoModelBase) m;
  }

  @Override
  public double[] convert(RowData data, double[] rawData) throws PredictException {
    rawData = super.convert(data, rawData);
    return _m.addExpandGamCols(rawData, data);
  }
}
