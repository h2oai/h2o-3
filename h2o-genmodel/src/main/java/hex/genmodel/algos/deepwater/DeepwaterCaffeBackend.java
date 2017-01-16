package hex.genmodel.algos.deepwater;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendParams;
import deepwater.backends.BackendTrain;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;

public class  DeepwaterCaffeBackend implements BackendTrain {
  // TODO
  @Override
  public void delete(BackendModel m) {
    // drop external process
  }

  @Override
  public BackendModel buildNet(ImageDataSet dataset, RuntimeOptions opts, BackendParams bparms, int num_classes, String name /*ignored for now*/) {
    if (name.equals("MLP")) {
      // TODO: add non-MLP Models such as lenet, inception_bn, etc.
      // Basic testing
      System.out.println("Caffe backend: building new MLP model");
      return new DeepwaterCaffeModel(
              num_classes,
              ((Integer) bparms.get("mini_batch_size")).intValue(),
              (int[]) bparms.get("hidden"),
              (String[]) bparms.get("activations"),
              ((Double) bparms.get("input_dropout_ratio")).doubleValue(),
              (double[]) bparms.get("hidden_dropout_ratios")
      );
    } else throw new UnsupportedOperationException("Only MLP is supported for now for Caffe.");
  }

  // TODO
  // graph (model definition) only
  @Override
  public void saveModel(BackendModel m, String model_path) {
    // Basic testing
    System.out.println("Caffe backend: saving MLP model");
    System.out.println("File path and name: " + model_path);
  }

  // TODO
  // full state of everything but the graph to continue training
  @Override
  public void loadParam(BackendModel m, String param_path) {
    // Basic testing
    System.out.println("Caffe backend: loading MLP parameters");
    System.out.println("File path and name: " + param_path);
  }

  // TODO
  // full state of everything but the graph to continue training
  @Override
  public void saveParam(BackendModel m, String param_path) {
    // Basic testing
    System.out.println("Caffe backend: saving MLP parameters");
    System.out.println("File path and name: " + param_path);
  }

  @Override
  public float[] loadMeanImage(BackendModel m, String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toJson(BackendModel m) {
    throw new UnsupportedOperationException();
  }

  // DONE
  @Override
  public void setParameter(BackendModel m, String name, float value) {
    // Basic testing
    System.out.println("Caffe backend: setting parameters");
    if (name.equals("learning_rate"))
      ((DeepwaterCaffeModel)m)._learning_rate = value;
    else if (name.equals("momentum"))
      ((DeepwaterCaffeModel)m)._momentum = value;
  }

  // TODO
  // given a mini-batch worth of data and labels, train
  @Override
  public float[]/*ignored*/ train(BackendModel m, float[/*mini_batch * input_neurons*/] data, float[/*mini_batch*/] label) {
    // Basic testing
    System.out.println("Caffe backend: building new MLP model");
    // implement training here

    return null; //return value is always ignored
  }

  @Override
  public float[] predict(BackendModel m, float[] data, float[] label) {
    throw new UnsupportedOperationException();
  }

  // TODO
  // return predictions (num_classes logits (softmax outputs) x mini_batch)
  @Override
  public float[/*mini_batch * num_classes*/] predict(BackendModel m, float[/*mini_batch * input_neurons*/] data) {
    // Basic testing
    System.out.println("Caffe backend: building new MLP model");
    DeepwaterCaffeModel cm = ((DeepwaterCaffeModel)m);
    return new float[cm.mini_batch_size*cm.num_classes];
  }

  private static class DeepwaterCaffeModel implements BackendModel {
    public DeepwaterCaffeModel(int num_classes, int mini_batch_size, int[] hidden, String[] activations, double input_dropout_ratio, double[] hidden_dropout_ratios) {
      this.num_classes = num_classes;
      this.mini_batch_size = mini_batch_size;
      this.hidden = hidden;
      this.activations = activations;
      this.input_dropout_ratio = input_dropout_ratio;
      this.hidden_dropout_ratios = hidden_dropout_ratios;
    }

    int num_classes;
    int mini_batch_size;
    int[] hidden;                   //neurons per layer
    String[] activations;           //per layer
    double input_dropout_ratio;
    double[] hidden_dropout_ratios; //per layer

    // other stuff
    float _learning_rate;
    float _momentum;
  }
}
