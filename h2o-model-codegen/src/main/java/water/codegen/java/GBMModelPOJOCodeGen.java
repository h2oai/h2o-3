package water.codegen.java;

import hex.Distribution;
import hex.tree.SharedTreeModel;
import hex.tree.gbm.GBMModel;
import water.codegen.java.mixins.GBMMixin;

import static water.codegen.java.JCodeGenUtil.*;

/**
 * FIXME:
 */
public class GBMModelPOJOCodeGen extends POJOModelCodeGenerator<GBMModelPOJOCodeGen, GBMModel> {

  public static GBMModelPOJOCodeGen codegen(GBMModel model) {
    return new GBMModelPOJOCodeGen(model);
  }

  protected GBMModelPOJOCodeGen(GBMModel model) {
    super(model);
  }

  @Override
  protected GBMModelPOJOCodeGen buildImpl(CompilationUnitGenerator cucg, ClassCodeGenerator ccg) {
    ccg.withMixin(model, SharedTreeModel.class);
    ccg.withMixin(model, GBMMixin.class);

    // Fill manual fields
    ccg.field("GEN_IS_BERNOULLI").withValue(VALUE(model._parms._distribution == Distribution.Family.bernoulli));

    // Implements linkInv method
    ccg.method("linkInv").withBody(s("return ").p(new Distribution(model._parms).linkInvString("f")).p(';')).withParentheses(true);

    // Implements scoreImpl method
    //ccg.method("scoreImpl").withBody(null);

    return self();
  }

}
