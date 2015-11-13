package water.codegen.java;

import javassist.compiler.CodeGen;

import java.lang.reflect.Modifier;

import water.codegen.CodeGenerator;
import water.codegen.HasId;
import water.codegen.JCodeSB;
import water.codegen.SimpleCodeGenerator;
import static water.codegen.java.JCodeGenUtil.s;

/**
 * Created by michal on 12/11/15.
 */
public class FieldCodeGenerator extends SimpleCodeGenerator<FieldCodeGenerator> {

  String comment;
  int modifiers;
  String type;
  String name;
  CodeGenerator initCode;

  public FieldCodeGenerator(String name) {
    this.name = name;
  }

  public FieldCodeGenerator withModifiers(int...modifiers) {
    for (int m : modifiers) {
      this.modifiers |= m;
    }
    return this;
  }

  public FieldCodeGenerator withType(String type) {
    this.type = type;
    return this;
  }

  public FieldCodeGenerator withComment(String comment) {
    this.comment = comment;
    return this;
  }

  public FieldCodeGenerator withValue(final JCodeSB initCode) {
    return withValue(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        out.p(initCode);
      }
    });
  }

  public FieldCodeGenerator withValue(CodeGenerator initCode) {
    this.initCode = initCode;
    return this;
  }

  @Override
  public void generate(JCodeSB out) {
    if (comment != null) {
      out.lineComment(comment).nl();
    }
    out.p(Modifier.toString(modifiers)).p(' ').p(type).p(' ').p(name);
    if (initCode != null) {
      out.p(" = ");
      initCode.generate(out);
    }
    out.p(';').nl(2);
  }
}
