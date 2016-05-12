package water.codegen.java;

import hex.Distribution;
import hex.deeplearning.DeepLearningModel;
import hex.deeplearning.DeepLearningModelInfo;
import hex.deeplearning.DeepLearningTask;
import hex.deeplearning.Neurons;
import water.codegen.java.mixins.DeepLearningModelMixin;

import static water.codegen.java.JCodeGenUtil.VALUE;
import static water.codegen.java.JCodeGenUtil.field;
import static water.codegen.java.JCodeGenUtil.s;

/**
 * Created by michal on 3/22/16.
 */
public class DeepLearningModelPOJOCodeGen
    extends POJOModelCodeGenerator<DeepLearningModelPOJOCodeGen, DeepLearningModel> {

  protected DeepLearningModelPOJOCodeGen(DeepLearningModel model) {
    super(model);
  }

  @Override
  protected DeepLearningModelPOJOCodeGen buildImpl(CompilationUnitGenerator cucg,
                                                   ClassCodeGenerator ccg) {
    // Inject all fields generators
    ccg.withMixin(model, DeepLearningModelMixin.class);

    final DeepLearningModelInfo modelInfo = model.model_info();

    // Extract Neurons
    final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(modelInfo);
    final int[] layers = new int[neurons.length];
    for (int i=0;i<neurons.length;++i)
      layers[i] = neurons[i].units();

    // 1. redefine body of nclasses - since it is different in generated code and runtime model
    ccg.method("nclasses").withBody(s("return ").p((modelInfo.get_params()._autoencoder ? neurons[neurons.length-1].units() : model._output.nclasses())).p(';'));

    // Generate values for given fields
    // NEURONS field
    ccg.field("NEURONS").withValue(VALUE(layers));
    // BIAS field - represented as lazy 2D array
    ccg.field("BIAS").withValue(VALUE(new JCodeGenUtil.ArrayWrapper(double[][].class) {
      @Override
      public Object get(int idx) {
        double[] bias = idx == 0 ? null : new double[modelInfo.get_biases(idx-1).size()];
        if (idx > 0) {
          for (int j=0; j<bias.length; ++j) bias[j] = modelInfo.get_biases(idx-1).get(j);
        }
        return new JCodeGenUtil.JavaArrayWrapper(bias, double[].class);
      }

      @Override
      public int getLen() {
        return neurons.length;
      }
    }));
    // WEIGHT field
    ccg.field("WEIGHT").withValue(VALUE(new JCodeGenUtil.ArrayWrapper(float[][].class) {
      @Override
      public Object get(int idx) {
        float[]
            weights = idx == 0 ? null : new float[modelInfo.get_weights(idx - 1).rows() * modelInfo.get_weights(idx - 1).cols()];
        if (idx > 0) {
          final int rows = modelInfo.get_weights(idx - 1).rows();
          final int cols = modelInfo.get_weights(idx - 1).cols();
          for (int j = 0; j < rows; ++j)
            for (int k = 0; k < cols; ++k)
              weights[j * cols + k] = modelInfo.get_weights(idx - 1).get(j, k);
        }
        return new JCodeGenUtil.JavaArrayWrapper(weights, float[].class);
      }

      @Override
      public int getLen() {
        return neurons.length;
      }
    }));

    // Define method link
    ccg.method("linkInv").withBody(s("return ").p(new Distribution(modelInfo.get_params()).linkInvString("f")).p(';'));

    // FIXME
    if (model.get_params()._autoencoder) {
      //sb.i(1).p("public int getPredsSize() { return " + modelInfo.units()[modelInfo.units().length-1] + "; }").nl();
      //sb.i(1).p("public String getHeader() { return \"" + model.getHeader() + "\"; }").nl();
    }

    return self();
  }
}
