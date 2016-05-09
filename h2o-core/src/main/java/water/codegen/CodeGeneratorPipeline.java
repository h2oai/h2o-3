package water.codegen;

import java.util.ArrayList;

import water.codegen.java.ClassGenContainer;
import water.codegen.java.CodeGeneratorB;
import water.codegen.java.HasBuild;

/**
 * A simple code generation pipeline.
 *
 * It composes code generators and allows for their execution
 * later.
 */
public class CodeGeneratorPipeline<S extends CodeGeneratorPipeline<S, E>, E extends CodeGenerator & HasBuild>
    extends ArrayList<E>
    implements CodeGenerator<S>, HasId<S>, HasBuild<S> {

  @Override
  public void generate(JCodeSB out) {
    for (int i = 0; i < this.size(); i++) {
      CodeGenerator cg = this.get(i);
      cg.generate(out);
    }
  }

  @Override
  public boolean add(E codeGenerator) {
    assert codeGenerator != null : "Ups - cannot append null code generator";
    return super.add(codeGenerator);
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

  final protected S self() {
    return (S) this;
  }

  final public void reset() {
    this.clear();
  }

  public /* abstract */ ClassGenContainer classContainer(CodeGenerator caller) {
    throw new RuntimeException("Should be overridden! Cannot be abstract now without breaking old API!");
  }

  @Override
  public S build() {
    for (int i = 0; i < this.size(); i++) {
      HasBuild cg = this.get(i);
      cg.build();
    }
    return self();
  }
}
