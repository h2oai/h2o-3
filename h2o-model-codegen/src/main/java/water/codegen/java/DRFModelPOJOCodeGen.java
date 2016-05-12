package water.codegen.java;

import hex.tree.SharedTreeModel;
import hex.tree.drf.DRFModel;
import water.codegen.java.mixins.DRFMixin;
import water.codegen.java.mixins.DeepLearningModelMixin;

import static water.codegen.java.JCodeGenUtil.s;

/**
 * FIXME:
 */
public class DRFModelPOJOCodeGen extends POJOModelCodeGenerator<DRFModelPOJOCodeGen, DRFModel> {

  public static DRFModelPOJOCodeGen codegen(DRFModel model) {
    return new DRFModelPOJOCodeGen(model);
  }

  protected DRFModelPOJOCodeGen(DRFModel model) {
    super(model);
  }

  @Override
  protected DRFModelPOJOCodeGen buildImpl(CompilationUnitGenerator cucg, ClassCodeGenerator ccg) {
    ccg.withMixin(model, SharedTreeModel.class);
    ccg.withMixin(model, DRFMixin.class);
    // Generate all trees

    return self();
  }

}
