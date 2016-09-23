package hex.deepwater.backends;

import hex.deepwater.datasets.DataSet;

/**
 * Created by fmilo on 9/23/16.
 */
class TensorflowBackend implements BackendTrain {

    @Override
    public void delete() {

    }

    @Override
    public void buildNet(DataSet dataset, RuntimeOptions opts, int num_classes, int batch_size, String name) {

    }

    @Override
    public void saveModel(String model_path) {

    }

    @Override
    public void loadParam(String param_path) {

    }

    @Override
    public void saveParam(String param_path) {

    }

    @Override
    public String toJson() {
        return null;
    }

    @Override
    public void setParameter(String name, float value) {

    }

    @Override
    public float[] train(float[] data, float[] label) {
        return new float[0];
    }

    @Override
    public float[] predict(float[] data, float[] label) {
        return new float[0];
    }

    @Override
    public float[] predict(float[] data) {
        return new float[0];
    }

}
