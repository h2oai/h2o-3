package water.codegen.java;

import hex.Distribution;
import hex.tree.SharedTreeModel;
import hex.tree.gbm.GBMModel;
import water.codegen.CodeGenerationService;
import water.codegen.java.mixins.GBMMixin;
import water.codegen.java.mixins.SharedTreeModelMixin;

import static water.codegen.java.JCodeGenUtil.*;

/**
 * Code generator for GBM model.
 *
 * It produces Java code based on {@link GBMMixin}.
 *
 * @see GBMMixin
 */
public class GBMModelPOJOCodeGen extends POJOModelCodeGenerator<GBMModelPOJOCodeGen, GBMModel> {

  protected GBMModelPOJOCodeGen(GBMModel model) {
    super(model);
  }

  @Override
  protected GBMModelPOJOCodeGen buildImpl(CompilationUnitGenerator cucg, ClassCodeGenerator ccg) {
    ccg.withMixin(model, SharedTreeModelMixin.class);
    ccg.withMixin(model, GBMMixin.class);

    // Fill manual fields
    ccg.field("GEN_IS_BERNOULLI").withValue(VALUE(model._parms._distribution == Distribution.Family.bernoulli));
    ccg.field("GEN_IS_MODIFIED_HUBER").withValue(VALUE(model._parms._distribution == Distribution.Family.modified_huber));

    // Implements linkInv method
    ccg.method("linkInv").withBody(s("return ").p(new Distribution(model._parms).linkInvString("pred")).p(';')).withParentheses(true);

    // Implements scoreImpl method
    ccg.method("scoreImpl").withBody(DRFModelPOJOCodeGen.treeScoreCodeGenerator(model, ccg)).withParentheses(true);

    return self();
  }

  public static class GBMGeneratorProvider extends GeneratorProvider<GBMModelPOJOCodeGen, GBMModel> {

    @Override
    public boolean supports(Class klazz) {
      return klazz.isAssignableFrom(GBMModel.class);
    }

    @Override
    public JavaCodeGenerator<GBMModelPOJOCodeGen, GBMModel> createGenerator(GBMModel model) {
      return new GBMModelPOJOCodeGen(model);
    }
  }
}
