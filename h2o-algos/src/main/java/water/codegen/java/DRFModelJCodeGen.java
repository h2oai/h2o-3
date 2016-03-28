package water.codegen.java;

import hex.tree.drf.DRFModel;

import static water.codegen.java.JCodeGenUtil.s;

/**
 * FIXME:
 */
public class DRFModelJCodeGen extends ModelCodeGenerator<DRFModelJCodeGen, DRFModel> {

  public static DRFModelJCodeGen codegen(DRFModel model) {
    return new DRFModelJCodeGen(model);
  }

  protected DRFModelJCodeGen(DRFModel model) {
    super(model);
  }

  @Override
  protected DRFModelJCodeGen buildImpl(CompilationUnitGenerator cucg, ClassCodeGenerator ccg) {
    MethodCodeGenerator score0MCG = ccg.method("score0");
    score0MCG.withBody(s("return pred;"));
    return self();
  }

}
