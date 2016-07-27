package water.codegen.java;

import water.codegen.CodeGeneratorPipeline;

/**
 * Top-level Java generator.
 *
 * It is composed of compilation units generators and strategy which provides
 * location where to generate a new classes (as separated compilation units
 * or in the same compilation unit.
 */
public abstract class JavaCodeGenerator<S extends JavaCodeGenerator<S, I>, I>
    extends CodeGeneratorPipeline<S, CompilationUnitGenerator>
    implements ClassGenContainer {

  public abstract static class GeneratorProvider<S extends JavaCodeGenerator<S, I>, I> {
    public abstract boolean supports(Class klazz);
    public abstract JavaCodeGenerator<S, I> createGenerator(I o);
  }

}
