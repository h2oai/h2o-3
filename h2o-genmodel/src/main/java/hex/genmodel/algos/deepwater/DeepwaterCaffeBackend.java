package hex.genmodel.algos.deepwater;

import java.util.Arrays;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendParams;
import deepwater.backends.BackendTrain;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;

/**
 * This backend forward requests to a docker images running the python
 * Caffe interface. C.f h20-docker/caffe for more information.
 */
public class DeepwaterCaffeBackend implements BackendTrain {
  @Override
  public void delete(BackendModel m) {
    ((DeepwaterCaffeModel) m).close();
  }

  @Override
  public BackendModel buildNet(ImageDataSet dataset, RuntimeOptions opts, BackendParams bparms, int num_classes, String name /*ignored for now*/) {
    if (name.equals("MLP")) {
      // TODO: add non-MLP Models such as lenet, inception_bn, etc.
      int[] hidden = (int[]) bparms.get("hidden");
      int[] sizes = new int[hidden.length + 2];
      sizes[0] = 0;  // why ?
      System.arraycopy(hidden, 0, sizes, 1, hidden.length);
      sizes[sizes.length - 1] = num_classes;
      // Basic information
      int num_layers = ((int[]) bparms.get("hidden")).length;
      System.out.println("Caffe backend: building new MLP model");
      System.out.println("  Parameters");
      System.out.format("  Mini batch size: %d \n", ((Integer) bparms.get("mini_batch_size")));
      System.out.format("  Hidden layers: %d \n", num_layers);
      //
      return new DeepwaterCaffeModel(
          (Integer) bparms.get("mini_batch_size"),
          sizes,
          (String[]) bparms.get("activations"),
          (double[]) bparms.get("hidden_dropout_ratios")
      );
    } else throw new UnsupportedOperationException("Only MLP is supported for now for Caffe.");
  }

  // graph (model definition) only
  @Override
  public void saveModel(BackendModel m, String model_path) {
    // Basic information
    System.out.println("Caffe backend: saving MLP model");
    System.out.println("File path and name: " + model_path);
    //
    ((DeepwaterCaffeModel) m).saveModel(model_path);
  }

  // full state of everything but the graph to continue training
  @Override
  public void loadParam(BackendModel m, String param_path) {
    // Basic information
    System.out.println("Caffe backend: loading MLP parameters");
    System.out.println("File path and name: " + param_path);
    //
    ((DeepwaterCaffeModel) m).loadParam(param_path);
  }

  // full state of everything but the graph to continue training
  @Override
  public void saveParam(BackendModel m, String param_path) {
    // Basic information
    System.out.println("Caffe backend: saving MLP parameters");
    System.out.println("File path and name: " + param_path);
    //
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
    // Basic information
    System.out.println("Caffe backend: setting parameter  "+name);
    //
    if (name.equals("learning_rate"))
      ((DeepwaterCaffeModel) m).learning_rate(value);
    else if (name.equals("momentum"))
      ((DeepwaterCaffeModel) m).momentum(value);
  }

  // given a mini-batch worth of data and labels, train
  @Override
  public float[]/*ignored*/ train(BackendModel m, float[/*mini_batch * input_neurons*/] data, float[/*mini_batch*/] label) {
    // Basic information
    System.out.format("Caffe backend: training   data size: %d  label size: %d \n", data.length, label.length);
    //
    ((DeepwaterCaffeModel) m).train(data, label);
    return null; //return value is always ignored
  }

  @Override
  public float[] predict(BackendModel m, float[] data, float[] label) {
    throw new UnsupportedOperationException();
  }

  // return predictions (num_classes logits (softmax outputs) x mini_batch)
  @Override
  public float[/*mini_batch * num_classes*/] predict(BackendModel m, float[/*mini_batch * input_neurons*/] data) {
    // new float[cm.mini_batch_size * cm.num_classes];
    // Basic information
    System.out.format("Caffe backend: predicting data size: %d \n", data.length);
    //
    return ((DeepwaterCaffeModel) m).predict(data);
  }
}
