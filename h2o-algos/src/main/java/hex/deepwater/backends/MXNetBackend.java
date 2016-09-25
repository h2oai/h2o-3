package hex.deepwater.backends;

import hex.deepwater.datasets.ImageDataSet;
import water.H2O;
import water.gpu.ImageTrain;
import water.util.Log;

import java.io.File;

class MXNetBackend implements BackendTrain {
  private ImageTrain _mxnet;

  @Override
  public void delete() {
    getTrainer().delete();
  }

  @Override
  public void buildNet(ImageDataSet dataset, RuntimeOptions opts, BackendParams bparms, int num_classes, String name) {
    assert(opts!=null);
    assert(dataset!=null);
    assert(bparms!=null);
    _mxnet = new ImageTrain(dataset.getWidth(), dataset.getHeight(), dataset.getChannels(),
        opts.getDeviceID()[0], (int)opts.getSeed(), opts.useGPU());
    if (bparms.get("clip_gradient") != null)
      _mxnet.setClipGradient(((Double)bparms.get("clip_gradient")).floatValue());
    if (bparms.get("hidden") == null) {
      getTrainer().buildNet(num_classes, ((Integer) bparms.get("mini_batch_size")).intValue(), name);
    } else {
      getTrainer().buildNet(
          num_classes,
          ((Integer) bparms.get("mini_batch_size")).intValue(),
          name,
          ((int[]) bparms.get("hidden")).length,
          (int[]) bparms.get("hidden"),
          (String[]) bparms.get("activations"),
          ((Double) bparms.get("input_dropout_ratio")).doubleValue(),
          (double[]) bparms.get("hidden_dropout_ratios")
      );
    }
  }

  @Override
  public void saveModel(String model_path) {
    getTrainer().saveModel(model_path);
  }

  @Override
  public void saveParam(String param_path) {
    getTrainer().saveParam(param_path);
  }

  @Override
  public String toJson() {
    return null;
  }

  @Override
  public void setParameter(String name, float value) {
    if (name == "momentum") {
      _mxnet.setMomentum(value);
    } else if (name == "learning_rate") {
      _mxnet.setLR(value);
    } else if (name == "clip_gradient") {
      _mxnet.setClipGradient(value);
    } else throw H2O.unimpl();
  }

  @Override
  public float[] train(float[] data, float[] label) {
    return getTrainer().train(data, label);
  }

  @Override
  public float[] predict(float[] data, float[] label) {
    return getTrainer().predict(data, label);
  }

  @Override
  public float[] predict(float[] data) {
    return getTrainer().predict(data);
  }

  @Override
  public void loadParam(String networkParms) {
    if (networkParms != null && !networkParms.isEmpty()) {
      File f = new File(networkParms);
      if (!f.exists() || f.isDirectory()) {
        Log.err("Parameter file " + f + " not found.");
      } else {
        Log.info("Loading the parameters (weights/biases) from: " + f.getAbsolutePath());
        getTrainer().loadParam(f.getAbsolutePath());
      }
    } else {
      Log.warn("No network parameters file specified. Starting from scratch.");
    }
  }

  ImageTrain getTrainer() {
    if (_mxnet == null) {
      _mxnet = new ImageTrain();
    }
    return _mxnet;
  }
}
