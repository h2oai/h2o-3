package water.codegen.java;

import hex.deeplearning.DeepLearningModel;
import hex.deeplearning.DeepLearningModelInfo;
import hex.deeplearning.DeepLearningTask;
import hex.deeplearning.Neurons;

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static water.codegen.java.JCodeGenUtil.field;
import static water.codegen.java.JCodeGenUtil.s;

/**
 * Created by michal on 3/22/16.
 */
public class DeepLearningModelJCodeGen extends ModelCodeGenerator<DeepLearningModelJCodeGen, DeepLearningModel> {

  protected DeepLearningModelJCodeGen(DeepLearningModel model) {
    super(model);
  }

  @Override
  protected DeepLearningModelJCodeGen buildImpl(CompilationUnitGenerator cucg,
                                                ClassCodeGenerator ccg) {

    final DeepLearningModelInfo modelInfo = model.model_info();
    if (modelInfo.data_info()._nums > 0) {
      //FIXME
      //sb.i(0).p("// Thread-local storage for input neuron activation values.").nl();
      //sb.i(0).p("final double[] NUMS = new double[" + model_info.data_info()._nums +"];").nl();

      ccg.withField(
          // FIXME should stress that field is STATIC
          // For example: STATIC().FIELD(...). OR STATIC_FIELD()
          FIELD(modelInfo.data_info()._normMul, "NORMMUL").withComment("Standardization/Normalization scaling factor for numerical variables."),
          FIELD(modelInfo.data_info()._normSub, "NORMSUB").withComment("Standardization/Normalization scaling factor for numerical variables.")
      );
    }

    if (modelInfo.data_info()._cats > 0) {
        ccg.withField(
            field(int[].class, "CATS").withComment("Workspace for storing categorical input variables.")
              .withModifiers(PUBLIC | STATIC | FINAL)
              .withValue(s("new int[").pj(modelInfo.data_info()._cats).p("]"))
        );
    }

    ccg.withField(
        FIELD(modelInfo.data_info()._catOffsets, "CATOFFSETS").withComment("Workspace for categorical offsets.")
    );

    if (modelInfo.data_info()._normRespMul != null) {
      ccg.withField(
          FIELD(modelInfo.data_info()._normRespMul, "NORMRESPMUL").withComment("Standardization/Normalization scaling factor for response."),
          FIELD(modelInfo.data_info()._normRespSub, "NORMRESPSUB").withComment("Standardization/Normalization offset for response.")
      );
    }
    if (modelInfo.get_params()._hidden_dropout_ratios != null) {
      ccg.withField(
          FIELD(modelInfo.get_params()._hidden_dropout_ratios, "HIDDEN_DRPOUT_RATIOS").withComment("Hidden layer dropout ratios.")
      );
    }

    final Neurons[] neurons = DeepLearningTask.makeNeuronsForTesting(modelInfo);
    final int[] layers = new int[neurons.length];
    for (int i=0;i<neurons.length;++i)
      layers[i] = neurons[i].units();

    ccg.withField(
        FIELD(layers, "NEURONS").withComment("Number of neurons for each layer.")
    );

    if (model.get_params()._autoencoder) {
      //sb.i(1).p("public int getPredsSize() { return " + modelInfo.units()[modelInfo.units().length-1] + "; }").nl();
      //sb.i(1).p("public String getHeader() { return \"" + model.getHeader() + "\"; }").nl();
    }

    return self();
  }
}
