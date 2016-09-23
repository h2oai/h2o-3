package hex.deepwater.backends;

import hex.deepwater.datasets.DataSet;
import water.gpu.ImageTrain;
import water.util.Log;

import java.io.File;

class MXNetBackend implements BackendTrain {

    private ImageTrain _imageTrain;

    @Override
    public void delete() {

    }

    @Override
    public void buildNet(DataSet dataset, RuntimeOptions opts, int num_classes, int batch_size, String name) {
        _imageTrain = new ImageTrain(dataset.getWidth(), dataset.getHeight(), dataset.getChannels(),
                opts.getDeviceID()[0],
                opts.getSeed(), opts.useGPU());
        _imageTrain.buildNet(num_classes, batch_size, name);
    }

    @Override
    public void saveModel(String model_path) {
        getModel().saveModel(model_path);
    }

    @Override
    public void saveParam(String param_path) {
        getModel().saveParam(param_path);
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
        return _imageTrain.train(data, label);
    }

    @Override
    public float[] predict(float[] data, float[] label) {
        return _imageTrain.predict(data, label);
    }

    @Override
    public float[] predict(float[] data) {
        return _imageTrain.predict(data);
    }

    @Override
    public void loadParam(String networkParms) {
        if (networkParms != null && !networkParms.isEmpty()) {
            File f = new File(networkParms);
            if (!f.exists() || f.isDirectory()) {
                Log.err("Parameter file " + f + " not found.");
            } else {
                Log.info("Loading the parameters (weights/biases) from: " + f.getAbsolutePath());
                getModel().loadParam(f.getAbsolutePath());
            }
        } else {
            Log.warn("No network parameters file specified. Starting from scratch.");
        }
    }

    ImageTrain getModel() {
      if (_imageTrain == null) {
        _imageTrain = new ImageTrain();
      }
      return _imageTrain;
    }
}
