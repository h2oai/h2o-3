package hex.genmodel;

import hex.genmodel.easy.EasyPredictModelWrapper.Config;
import hex.genmodel.easy.EasyPredictModelWrapper.ErrorConsumer;
import hex.genmodel.easy.RowToRawDataConverter;


public interface MojoPreprocessor {
    
    ModelProcessor makeProcessor(GenModel model);

    
    interface ModelProcessor {
        /**
         * @param errorConsumer
         * @param config
         * @return a new {@link RowToRawDataConverter} adapted to the given model
         */
        RowToRawDataConverter makeRowConverter(ErrorConsumer errorConsumer,
                                               Config config);

        /**
         * @return a
         */
        GenModel getProcessedModel();
    }
}
