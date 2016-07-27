package water.codegen.java;

import hex.pca.PCA;
import hex.pca.PCAModel;
import water.codegen.java.mixins.PCAModelMixin;

/**
 * PCA model code generator.
 */
public class PCAModelPOJOCodeGen extends POJOModelCodeGenerator<PCAModelPOJOCodeGen, PCAModel> {

  protected PCAModelPOJOCodeGen(PCAModel model) {
    super(model);
  }

  @Override
  protected PCAModelPOJOCodeGen buildImpl(CompilationUnitGenerator cucg, ClassCodeGenerator ccg) {
    ccg.withMixin(model, PCAModelMixin.class);
    return self();
  }
}
