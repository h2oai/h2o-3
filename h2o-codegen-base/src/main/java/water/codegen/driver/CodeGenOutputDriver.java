package water.codegen.driver;

import java.io.IOException;
import java.io.OutputStream;

import water.codegen.java.JavaCodeGenerator;

/**
 * Stateless class to forward output
 * of code generator into given output.
 */
abstract public class CodeGenOutputDriver {
  abstract public void codegen(JavaCodeGenerator<?, ?> mcg, OutputStream os) throws IOException;
}
