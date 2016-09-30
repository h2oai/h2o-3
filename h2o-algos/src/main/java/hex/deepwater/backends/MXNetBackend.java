package hex.deepwater.backends;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendParams;
import deepwater.backends.BackendTrain;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;
import water.H2O;
import water.util.Log;

import java.io.File;

class MXNetBackend implements BackendTrain {
  MXNetBackendModel get(BackendModel m) {
    return (MXNetBackendModel) m;
  }

  @Override
  public void delete(BackendModel m) {
    get(m).delete();
  }

  @Override
  public BackendModel buildNet(ImageDataSet dataset, RuntimeOptions opts, BackendParams bparms, int num_classes, String name) {
    assert(opts!=null);
    assert(dataset!=null);
    assert(bparms!=null);
    MXNetBackendModel mxnet = new MXNetBackendModel(dataset.getWidth(), dataset.getHeight(), dataset.getChannels(),
        opts.getDeviceID()[0], (int)opts.getSeed(), opts.useGPU());

    if (bparms.get("clip_gradient") != null)
      mxnet.setClipGradient(((Double)bparms.get("clip_gradient")).floatValue());
    if (bparms.get("hidden") == null) {
      mxnet.buildNet(num_classes, ((Integer) bparms.get("mini_batch_size")).intValue(), name);
    } else {
      mxnet.buildNet(
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
    return mxnet;
  }


  @Override
  public void setParameter(BackendModel m, String name, float value) {
    MXNetBackendModel mxnet = get(m);
    if (name == "momentum") {
      mxnet.setMomentum(value);
    } else if (name == "learning_rate") {
      mxnet.setLR(value);
    } else if (name == "clip_gradient") {
      mxnet.setClipGradient(value);
    } else throw H2O.unimpl();
  }

  @Override
  public float[] train(BackendModel m, float[] data, float[] label) {
    return get(m).train(data, label);
  }

  @Override
  public float[] predict(BackendModel m, float[] data, float[] label) {
    return get(m).predict(data, label);
  }

  @Override
  public float[] predict(BackendModel m, float[] data) {
    return get(m).predict(data);
  }

  @Override
  public void loadParam(BackendModel m, String networkParms) {
    MXNetBackendModel model = get(m);

    if (networkParms != null && !networkParms.isEmpty()) {
      File f = new File(networkParms);
      if (!f.exists() || f.isDirectory()) {
        Log.err("Parameter file " + f + " not found.");
      } else {
        Log.info("Loading the parameters (weights/biases) from: " + f.getAbsolutePath());
        model.loadParam(f.getAbsolutePath());
      }
    } else {
      Log.warn("No network parameters file specified. Starting from scratch.");
    }
  }

  @Override
  public void saveModel(BackendModel m, String model_path) {
    get(m).saveModel(model_path);
  }

  @Override
  public void saveParam(BackendModel m, String param_path) {
    get(m).saveParam(param_path);
  }

  @Override
  public String toJson(BackendModel m) {
    return null;
  }

}
