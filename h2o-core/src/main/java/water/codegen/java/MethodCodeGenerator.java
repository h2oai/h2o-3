package water.codegen.java;

import java.lang.reflect.Modifier;

import water.codegen.*;

import static water.codegen.JCodeGen.*;
import static water.util.ArrayUtils.append;


/**
 * FIXME: WIP
 */
public class MethodCodeGenerator extends CodeGeneratorPipeline<MethodCodeGenerator> {

  public static MethodCodeGenerator codegen(String name) {
    return new MethodCodeGenerator(name);
  }

  final String name;

  private int modifiers;
  private Class[] paramTypes;
  private String[] paramNames;
  private Class returnType = void.class;

  protected MethodCodeGenerator(String name) {
    this.name = name;
  }

  public MethodCodeGenerator withModifiers(int...modifiers) {
    for (int m : modifiers) {
      this.modifiers |= m;
    }
    return this;
  }

  public MethodCodeGenerator withReturnType(Class returnType) {
    this.returnType = returnType;
    return this;
  }

  public MethodCodeGenerator withBody(CodeGenerator codegen) {
    add(codegen);
    return this;
  }

  public MethodCodeGenerator withBody(final JCodeSB body) {
    add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        out.p(body);
      }
    });
    return this;
  }

  public MethodCodeGenerator withParams(Class type, String name) {
    this.paramTypes = append(this.paramTypes, type);
    this.paramNames = append(this.paramNames, name);
    return this;
  }

  @Override
  public void generate(JCodeSB out) {
    // Output method preamble
    pMethodParams(out.p(Modifier.toString(modifiers)).p(' ').pj(returnType).p(' ').p(name).p('('), paramTypes, paramNames).p(") {").ii(2).nl();
    // Generate method body
    super.generate(out);
    // Close method
    out
        .di(2).nl()
        .p("} // End of method ").p(name).nl(2);
  }
}
