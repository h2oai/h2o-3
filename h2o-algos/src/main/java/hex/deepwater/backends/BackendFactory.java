package hex.deepwater.backends;


import deepwater.backends.BackendTrain;
import hex.deepwater.DeepWaterParameters;
import water.H2O;


public class BackendFactory {

    static public BackendTrain create(DeepWaterParameters.Backend _backend) {
        switch (_backend)  {
            case tensorflow:
               throw H2O.unimpl();
            case mxnet:
                return new MXNetBackend();
            default:
                throw H2O.unimpl();
        }
    }
}