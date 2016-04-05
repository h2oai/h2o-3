package water.codegen.driver;

import java.io.IOException;
import java.io.OutputStream;

import water.codegen.SBPrintStream;
import water.codegen.java.CompilationUnitGenerator;
import water.codegen.java.POJOModelCodeGenerator;

/**
 * Generate all output into a single output.
 */
public class DirectOutputDriver extends CodeGenDriver {

  @Override
  public void codegen(POJOModelCodeGenerator<?, ?> mcg, OutputStream os) throws IOException {
    SBPrintStream sbos = new SBPrintStream(os);
    try {
      // FIXME need to create a new top-level CU and attach all classes under it
      // + handle all specific corner-cases for single java file
      for (int i = 0; i < mcg.size(); i++) {
        CompilationUnitGenerator cug = mcg.get(i);
        cug.generate(sbos);
      }
    } finally {
      // Do nothing, since owner of stream should close it
    }
  }
}
