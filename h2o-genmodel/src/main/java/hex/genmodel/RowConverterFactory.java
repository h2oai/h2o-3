package hex.genmodel;

import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowToRawDataConverter;

public interface RowConverterFactory {

    /**
     * @return A new instance of {@link RowToRawDataConverter}
     */
    RowToRawDataConverter makeRowConverter(CategoricalEncoding categoricalEncoding,
                                           EasyPredictModelWrapper.ErrorConsumer errorConsumer,
                                           EasyPredictModelWrapper.Config config);
}
