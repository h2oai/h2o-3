package water.codegen.java;

import hex.tree.drf.DRFModel;

import static water.codegen.java.JCodeGenUtil.s;

/**
 * FIXME:
 */
public class DRFPOJOModelJCodeGen extends POJOModelCodeGenerator<DRFPOJOModelJCodeGen, DRFModel> {

  public static DRFPOJOModelJCodeGen codegen(DRFModel model) {
    return new DRFPOJOModelJCodeGen(model);
  }

  protected DRFPOJOModelJCodeGen(DRFModel model) {
    super(model);
  }

  @Override
  protected DRFPOJOModelJCodeGen buildImpl(CompilationUnitGenerator cucg, ClassCodeGenerator ccg) {
    MethodCodeGenerator score0MCG = ccg.method("score0");
    score0MCG.withBody(s("return pred;"));
    return self();
  }

}
