package hex.genmodel;

import hex.genmodel.easy.EasyPredictModelWrapper.Config;
import hex.genmodel.easy.EasyPredictModelWrapper.ErrorConsumer;
import hex.genmodel.easy.RowToRawDataConverter;


public interface MojoTransformer {
    
    DataTransformer makeDataTransformer(GenModel model);

    
    interface DataTransformer {
        /**
         * @param errorConsumer
         * @param config
         * @return a new {@link RowToRawDataConverter} adapted to the given model
         */
        RowToRawDataConverter makeRowConverter(ErrorConsumer errorConsumer,
                                               Config config);

        /**
         * @return a model that ...
         */
        GenModel getTransformedModel();
    }
}
