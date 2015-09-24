package water.codegen;

import java.util.ArrayList;

import water.exceptions.JCodeSB;

/**
 * A simple code generation pipeline.
 *
 * It composes code generators and allows for their execution
 * later.
 */
public class CodeGeneratorPipeline extends ArrayList<CodeGenerator> implements
                                                                    CodeGenerator {

  @Override
  public void generate(JCodeSB out) {
    for (CodeGenerator codeGen : this) {
      codeGen.generate(out);
    }
  }
}
