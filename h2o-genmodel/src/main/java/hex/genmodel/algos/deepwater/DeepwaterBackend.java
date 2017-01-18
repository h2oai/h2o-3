package hex.genmodel.algos.deepwater;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendParams;
import deepwater.backends.BackendTrain;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;

public class DeepwaterBackend implements BackendTrain {
  // TODO
  @Override
  public void delete(BackendModel m) {
    // drop external process
  }

  @Override
  public BackendModel buildNet(ImageDataSet dataset, RuntimeOptions opts, BackendParams bparms, int num_classes, String name /*ignored for now*/) {
    if (name.equals("MLP")) {
      // TODO: add non-MLP Models such as lenet, inception_bn, etc.
      return new DeepwaterModel(
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

  }

  // TODO
  // full state of everything but the graph to continue training
  @Override
  public void loadParam(BackendModel m, String param_path) {

  }

  // TODO
  // full state of everything but the graph to continue training
  @Override
  public void saveParam(BackendModel m, String param_path) {

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
    if (name.equals("learning_rate"))
      ((DeepwaterCaffeModel) m)._learning_rate = value;
    else if (name.equals("momentum"))
      ((DeepwaterCaffeModel) m)._momentum = value;
  }

  // TODO
  // given a mini-batch worth of data and labels, train
  @Override
  public float[]/*ignored*/ train(BackendModel m, float[/*mini_batch * input_neurons*/] data, float[/*mini_batch*/] label) {
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
    DeepwaterModel cm = ((DeepwaterModel) m);
    return new float[cm.mini_batch_size * cm.num_classes];
  }

  private static class DeepwaterModel implements BackendModel {
    public DeepwaterModel(int num_classes, int mini_batch_size, int[] hidden, String[] activations, double input_dropout_ratio, double[] hidden_dropout_ratios) {
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

  /**
   * DeepwaterLayer is the basic class used for backend implementations of a layer
   * Layer: array of units (neurons) sharing parameters (activation, etc.)
   */

  private static class DeepwaterLayer {
    public DeepwaterLayer(String name, String type, int[] shape, int width, String[] activation, double dropout_ratio,
                          String type_spec, int[] incidence, LayerLearning pars, byte[] weights, byte[] biases) {
      this.name = name;
      this.type = type;
      this.width = width;
      this.activation = activation;
      this.dropout_ratio = dropout_ratio;
      this.type_spec = type_spec;
      this.incidence = incidence;
      this.learning_pars = pars;
      this.weights = weights;
      this.biases = biases;
    }
    // Fixed parameters
    String name;              // Layer name that describe this node (should be unique)
    String type;              // Layer type: input (buffer), full/sparse connected, convolution, etc.
    int [] shape;             // Dimensions of the layer (may be used used for input/output layers)
    int width;                // Layer width: number of units (neurons), output vector size
    String activation;        // Activation function for the layer (one per layer)
    double dropout_ratio;     // Dropout factor for the layer
    String type_spec;         // String for type structure specification (if needed Json)
    int [] incidence;         // Incidence list, for linear topology it's just previous layer
    // Variable parameters
    LayerLearning learning_pars; // Learning parameters for layer (adjustable)
    byte[] weights;           // Values of weight matrix
    byte[] biases;            // Values of bias vector
  }

  /**
   * LayerLearning contains adaptable learning parameters for layer
   *
   */

  private static class LayerLearning {
    public LayerLearning(String name, String type, int[] shape, int width, String[] activation, double dropout_ratio,
                          String type_spec, int[] incidence, LayerLearning pars, byte[] weights, byte[] biases) {

    }


    }










}