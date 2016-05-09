package water.codegen.java;

import water.codegen.CodeGenerator;
import water.codegen.JCodeSB;

/**
 * Created by michal on 5/6/16.
 */
public class CodeGeneratorB<S extends CodeGenerator<S>> implements CodeGenerator<S>, HasBuild<S> {

  @Override
  public void generate(JCodeSB out) {

  }

  @Override
  public S build() {
    return self();
  }

  protected final S self() {
    return (S) this;
  }
}
