package hex.deepwater.backends;

import hex.deepwater.datasets.ImageDataSet;

class TensorflowBackend implements BackendTrain {

    @Override
    public void delete() {

    }

    @Override
    public void buildNet(ImageDataSet dataset, RuntimeOptions opts, BackendParams backend_params, int num_classes, String name) {

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

    @Override
    public void setupSession(RuntimeOptions opts, int classes, int mini_batch_size, String network) {

    }

}
