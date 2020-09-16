package hex.genmodel;

import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowToRawDataConverter;

public interface RowConverterFactory {

    /**
     * Prefixed by _ as it is an internal API (skipped for validation by Mojoland).
     * @return A new instance of {@link RowToRawDataConverter}
     */
    RowToRawDataConverter _makeRowConverter(CategoricalEncoding categoricalEncoding,
                                            EasyPredictModelWrapper.ErrorConsumer errorConsumer,
                                            EasyPredictModelWrapper.Config config);
}
