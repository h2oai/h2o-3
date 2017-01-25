package hex.genmodel.algos.deepwater.caffe;

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
      // Neurons per layer
      int[] hidden = (int[]) bparms.get("hidden");  // Hidden layers sizes are obtained
      int[] sizes = new int[hidden.length + 2];     // Sizes array includes input and output sizes
      System.arraycopy(hidden, 0, sizes, 1, hidden.length);
      sizes[0] = 0;                                 // Input size set to 0! see data information
      sizes[sizes.length - 1] = num_classes;        // Last size set to num_classes
      // Activation functions
      String[] act = new String[hidden.length + 2];
      System.arraycopy((String[]) bparms.get("activations"), 0, act, 1, hidden.length);
      act[0]            = "input";                   // Input type
      act[act.length-1] = "output";                  // Input type
      // Drop out ratios
      double[] dropout = new double[hidden.length + 2];
      System.arraycopy((double[]) bparms.get("hidden_dropout_ratios"), 0, dropout, 1, hidden.length);
      dropout[0] = (double) bparms.get("input_dropout_ratio");
      dropout[act.length-1] = 0.0;                    // Output dropout
      return new DeepwaterCaffeModel(
          (Integer) bparms.get("mini_batch_size"),    // Batch size information
          sizes,
          act,
          dropout
      );
    } else throw new UnsupportedOperationException("Only MLP is supported for now for Caffe.");
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
    if (name.equals("learning_rate"))
      ((DeepwaterCaffeModel) m).learning_rate(value);
    else if (name.equals("momentum"))
      ((DeepwaterCaffeModel) m).momentum(value);
  }

  // given a mini-batch worth of data and labels, train
  @Override
  public float[]/*ignored*/ train(BackendModel m, float[/*mini_batch * input_neurons*/] data, float[/*mini_batch*/] label) {
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
    return ((DeepwaterCaffeModel) m).predict(data);
  }
}
