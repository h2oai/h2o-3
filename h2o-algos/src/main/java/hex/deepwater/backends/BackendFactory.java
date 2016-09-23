package hex.deepwater.backends;

import hex.deepwater.DeepWaterParameters;
import water.H2O;

/**
 * Created by fmilo on 9/23/16.
 */
public class BackendFactory {

    static public BackendTrain Create(DeepWaterParameters.Backend _backend) {
        switch (_backend)  {
            case tensorflow:
                return new TensorflowBackend();
            case mxnet:
                return new MXNetBackend();
            default:
              throw H2O.unimpl();
        }
    }
}
