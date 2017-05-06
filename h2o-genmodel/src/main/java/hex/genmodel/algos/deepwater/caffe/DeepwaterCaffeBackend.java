package hex.genmodel.algos.deepwater.caffe;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendParams;
import deepwater.backends.BackendTrain;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * This backend forward requests to a docker images running the python
 * Caffe interface. C.f h20-docker/caffe for more information.
 */
public class DeepwaterCaffeBackend implements BackendTrain {
  static public final String CAFFE_DIR = "/opt/caffe/";
  static public final String CAFFE_H2O_DIR = "/opt/caffe-h2o/";

  @Override
  public void delete(BackendModel m) {
    ((DeepwaterCaffeModel) m).close();
  }

  @Override
  public BackendModel buildNet(ImageDataSet dataset, RuntimeOptions opts, BackendParams bparms, int num_classes, String name) {
    if (name.equals("MLP")) {
      int[] hidden = (int[]) bparms.get("hidden");
      int[] sizes = new int[hidden.length + 2];
      sizes[0] = dataset.getWidth();
      System.arraycopy(hidden, 0, sizes, 1, hidden.length);
      sizes[sizes.length - 1] = num_classes;
      System.err.println("Ignoring device_id");
      double[] hdr = new double[sizes.length];
      if (bparms.get("input_dropout_ratio") != null)
        hdr[0] = (double) bparms.get("input_dropout_ratio");
      double[] bphdr = (double[]) bparms.get("hidden_dropout_ratios");
      if (bphdr != null)
        System.arraycopy(bphdr, 0, hdr, 1, bphdr.length);
      String[] layers = new String[sizes.length];
      System.arraycopy(bparms.get("activations"), 0, layers, 1, hidden.length);
      layers[0] = "data";
      layers[layers.length - 1] = "loss";

      return new DeepwaterCaffeModel(
          (Integer) bparms.get("mini_batch_size"),
          sizes,
          layers,
          hdr,
          opts.getSeed(),
          opts.useGPU()
      );
    } else {
      return new DeepwaterCaffeModel(
          name,
          new int[] {
              (Integer) bparms.get("mini_batch_size"),
              dataset.getChannels(),
              dataset.getWidth(),
              dataset.getHeight()
          },
          opts.getSeed(),
          opts.useGPU()
      );
    }
  }

  // graph (model definition) only
  @Override
  public void saveModel(BackendModel m, String model_path) {
    ((DeepwaterCaffeModel) m).saveModel(model_path);
  }

  // full state of everything but the graph to continue training
  @Override
  public void loadParam(BackendModel m, String param_path) {
    ((DeepwaterCaffeModel) m).loadParam(param_path);
  }

  // full state of everything but the graph to continue training
  @Override
  public void saveParam(BackendModel m, String param_path) {
    ((DeepwaterCaffeModel) m).saveParam(param_path);
  }

  @Override
  public float[] loadMeanImage(BackendModel m, String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toJson(BackendModel m) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setParameter(BackendModel m, String name, float value) {
//    if (name.equals("learning_rate"))
//      ((DeepwaterCaffeModel) m).learning_rate(value);
//    else if (name.equals("momentum"))
//      ((DeepwaterCaffeModel) m).momentum(value);
  }

  // given a mini-batch worth of data and labels, train
  @Override
  public float[]/*ignored*/ train(BackendModel m, float[/*mini_batch * input_neurons*/] data, float[/*mini_batch*/] label) {
    ((DeepwaterCaffeModel) m).train(data, label);
    return null; //return value is always ignored
  }

  // return predictions (num_classes logits (softmax outputs) x mini_batch)
  @Override
  public float[/*mini_batch * num_classes*/] predict(BackendModel m, float[/*mini_batch * input_neurons*/] data) {
    // new float[cm.mini_batch_size * cm.num_classes];
    return ((DeepwaterCaffeModel) m).predict(data);
  }

  @Override
  public void deleteSavedModel(String model_path) {

  }

  @Override
  public void deleteSavedParam(String param_path) {

  }

  @Override
  public String listAllLayers(BackendModel m) {
    return null;
  }

  @Override
  public float[] extractLayer(BackendModel m, String name, float[] data) {
    return new float[0];
  }

  public void writeBytes(File file, byte[] payload) throws IOException {
    FileOutputStream os = new FileOutputStream(file.toString());
    os.write(payload);
    os.close();
  }

  public byte[] readBytes(File file) throws IOException {
    FileInputStream is = new FileInputStream(file);
    byte[] params = new byte[(int)file.length()];
    is.read(params);
    is.close();
    return params;
  }
}
