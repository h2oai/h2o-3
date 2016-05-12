package water.codegen;

import water.codegen.java.ClassGenContainer;
import water.codegen.java.CodeGeneratorB;
import water.codegen.java.HasBuild;

/**
 * FIXME:
 */
abstract public class SimpleCodeGenerator<S extends SimpleCodeGenerator<S>> extends CodeGeneratorB<S>
    implements HasId<S> {

  String id;

  @Override
  public S withId(String id) {
    this.id = id;
    return (S) this;
  }

  @Override
  public String id() {
    return id;
  }

  // FIXME: extract to interface
  public abstract ClassGenContainer classContainer(CodeGenerator caller);
}
