package hex.deepwater.backends;

import deepwater.backends.BackendModel;
import water.gpu.ImageTrain;

public class MXNetBackendModel extends ImageTrain implements BackendModel {

    public MXNetBackendModel(int width, int height, int channels, int i, int seed, boolean b) {
       super(width, height, channels, i , seed, b);
    }
}
