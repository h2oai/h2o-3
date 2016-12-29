package ai.h2o.cascade.vals;

import ai.h2o.cascade.asts.AstNode;

/**
 *
 */
public class ValAst extends Val {
  private AstNode ast;


  public ValAst(AstNode v) {
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
  public AstNode getAst() {
    return ast;
  }
}
