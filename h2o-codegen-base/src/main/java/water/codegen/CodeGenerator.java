package water.codegen;

/**
 * Interface for code generator.
 */
public interface CodeGenerator<S extends CodeGenerator> {

  /** Generate code to given output.
   *
   * @param out  code generation output.
   */
  void generate(JCodeSB out);

}

