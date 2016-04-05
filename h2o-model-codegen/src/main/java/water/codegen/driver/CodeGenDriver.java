package water.codegen.driver;

import java.io.IOException;
import java.io.OutputStream;

import water.codegen.java.POJOModelCodeGenerator;

/**
 * Stateless class to forward output
 * of code generator into given output.
 */
abstract public class CodeGenDriver {
  abstract public void codegen(POJOModelCodeGenerator<?, ?> mcg, OutputStream os) throws IOException;
}
