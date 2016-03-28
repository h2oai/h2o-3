package water.codegen.driver;

import java.io.IOException;
import java.io.OutputStream;

import water.codegen.java.ModelCodeGenerator;

/**
 * Stateless class to forward output
 * of code generator into given output.
 */
abstract public class CodeGenDriver {
  abstract public void codegen(ModelCodeGenerator<?, ?> mcg, OutputStream os) throws IOException;
}
