package water.codegen.java;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import hex.genmodel.GenModel;
import water.H2O;
import water.codegen.JCodeGen;
import water.codegen.driver.CodeGenDriver;
import water.codegen.driver.DirectOutputDriver;

/**
 * Shared utilities for CodeGen tests.
 */
public class CodeGenTestUtil {

  static GenModel getPojoModel(POJOModelCodeGenerator modelCodeGen) {
    return getPojoModel(modelCodeGen, new DirectOutputDriver());
  }

  static GenModel getPojoModel(POJOModelCodeGenerator modelCodeGen, CodeGenDriver cgd) {
    CodeGenDriver driver = new DirectOutputDriver();
    //FileOutputStream fos = new FileOutputStream(new File("/tmp/testmodel.java"));
    ByteArrayOutputStream fos = new ByteArrayOutputStream(1 << 16);
    GenModel genModel = null;
    try {
      // FIXME: i cannot switch the driver without switching a target
      // Probably builder patter would be better here
      driver.codegen(modelCodeGen, fos);
      // Compile code
      String javaModelCode = new String(fos.toByteArray());
      String javaModelId = modelCodeGen.getModelName();
      Class klazzGenModel = JCodeGen.compile(javaModelId, javaModelCode);
      return (GenModel) klazzGenModel.newInstance();
    } catch (Exception e) {
      throw H2O.fail("Model compilation failed", e);
    } finally {
      try { fos.close(); } catch (IOException ioe) { /* ignore */ }
    }
  }

}
