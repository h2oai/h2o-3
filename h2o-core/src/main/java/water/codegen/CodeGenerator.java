package water.codegen;

/**
 * Interface for code generator.
 */
public interface CodeGenerator {

  /** Generate code to given output.
   *
   * @param out  code generation output.
   */
  void generate(JCodeSB out);

  // FIXME
  /* Impact on java bytecode
  int javaConstantPool();

  int javaBytecodeLen();
  */

}

