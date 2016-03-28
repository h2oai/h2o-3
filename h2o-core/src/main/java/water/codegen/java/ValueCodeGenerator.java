package water.codegen.java;

import water.codegen.CodeGenerator;
import water.codegen.JCodeSB;
import water.codegen.SimpleCodeGenerator;

/**
 * Created by michal on 3/25/16.
 *
 */
public abstract class ValueCodeGenerator<S extends ValueCodeGenerator<S>> extends SimpleCodeGenerator<S> {

  private FieldCodeGenerator fcg;

  final void setFcg(FieldCodeGenerator fcg) {
    this.fcg = fcg;
  }
  final FieldCodeGenerator fcg() {
    return fcg;
  }

  final public String prefix() {
    return fcg().name + "_HOLDER";
  }

  @Override
  final public ClassGenContainer classContainer(CodeGenerator caller) {
    return fcg().classContainer(caller);
  }

  protected ClassGenContainer classContainer() {
    return classContainer(this);
  }
}


