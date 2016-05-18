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

}
