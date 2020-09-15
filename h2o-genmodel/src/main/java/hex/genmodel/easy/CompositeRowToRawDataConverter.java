package hex.genmodel.easy;

import hex.genmodel.GenModel;
import hex.genmodel.easy.exception.PredictException;

public class CompositeRowToRawDataConverter<M extends GenModel> implements RowToRawDataConverter {

    private RowToRawDataConverter[] _converters;
    
    public CompositeRowToRawDataConverter(RowToRawDataConverter[] converters) {
        _converters = converters;
    }

    @Override
    public double[] convert(RowData data, double[] rawData) throws PredictException {
        double[] raw = rawData;
        for (RowToRawDataConverter converter: _converters) {
            raw = converter.convert(data, raw);
        }
        return raw;
    }
}
