package water.codegen;

import java.util.ArrayList;

/**
 * A simple code generation pipeline.
 *
 * It composes code generators and allows for their execution
 * later.
 */
public class CodeGeneratorPipeline<S extends CodeGeneratorPipeline<S>> extends ArrayList<CodeGenerator> implements
                                                                    CodeGenerator,
                                                                    HasId<S> {

  @Override
  public void generate(JCodeSB out) {
    for (CodeGenerator codeGen : this) {
      codeGen.generate(out);
    }
  }

  private String id;

  @Override
  public S withId(String id) {
    this.id = id;
    return self();
  }

  @Override
  public String id() {
    return id;
  }

  protected S self() {
    return (S) this;
  }
}
