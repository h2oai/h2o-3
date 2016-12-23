package ai.h2o.cascade.vals;

import ai.h2o.cascade.asts.Ast;

/**
 *
 */
public class ValAst extends Val {
  private Ast ast;


  public ValAst(Ast v) {
    ast = v;
  }

  @Override
  public Type type() {
    return Type.AST;
  }

  @Override
  public boolean isAst() {
    return true;
  }

  @Override
  public Ast getAst() {
    return ast;
  }
}
