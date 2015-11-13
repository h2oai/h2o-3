package water.codegen.java;

import hex.Model;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;

/**
 * FIXME:
 */
public class DRFModelCodeGen extends ModelCodeGenerator<DRFModelCodeGen, DRFModel> {

  public static DRFModelCodeGen codegen(DRFModel model) {
    return new DRFModelCodeGen(model);
  }

  protected DRFModelCodeGen(DRFModel model) {
    super(model);
  }

  @Override
  protected DRFModelCodeGen buildImpl(CompilationUnitGenerator cucg, ClassCodeGenerator ccg) {
    return self();
  }
}
