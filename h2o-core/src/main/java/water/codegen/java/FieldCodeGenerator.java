package water.codegen.java;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import water.codegen.CodeGenerator;
import water.codegen.JCodeSB;
import water.codegen.SimpleCodeGenerator;
import static water.codegen.java.JCodeGenUtil.s;

/**
 * Generator for a field defined by type name and initialized value.
 */
public class FieldCodeGenerator extends SimpleCodeGenerator<FieldCodeGenerator> {

  String comment;
  int modifiers;
  // FIXME: can we have here Class<?> ?
  Class type;
  String name;
  CodeGenerator initCode;

  private ClassCodeGenerator ccg;

  public FieldCodeGenerator(Field f) {
    withName(f.getName());
    withModifiers(f.getModifiers());
    withType(f.getType());
  }

  public FieldCodeGenerator(String name) {
    this.name = name;
  }

  public FieldCodeGenerator withModifiers(int...modifiers) {
    for (int m : modifiers) {
      this.modifiers |= m;
    }
    return this;
  }

  public FieldCodeGenerator withType(Class type) {
    this.type = type;
    return this;
  }

  public FieldCodeGenerator withComment(String comment) {
    this.comment = comment;
    return this;
  }

  public FieldCodeGenerator withValue(final JCodeSB initCode) {
    return withValue(new ValueCodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        out.p(initCode);
      }
    });
  }

  public FieldCodeGenerator withValue(ValueCodeGenerator initCode) {
    this.initCode = initCode;
    initCode.setFcg(this);
    return this;
  }

  public FieldCodeGenerator withName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public void generate(JCodeSB out) {
    if (comment != null) {
      out.lineComment(comment).nl();
    }
    out.p(Modifier.toString(modifiers)).p(' ').pj(type).p(' ').p(name);
    if (initCode != null) {
      out.p(" = ");
      initCode.generate(out);
    }
    out.p(';').nl(2);
  }

  ClassCodeGenerator ccg() {
    return ccg;
  }

  void setCcg(ClassCodeGenerator ccg) {
    this.ccg = ccg;
  }

  @Override
  final public ClassGenContainer classContainer(CodeGenerator caller) {
    return ccg.classContainer(caller);
  }
}
