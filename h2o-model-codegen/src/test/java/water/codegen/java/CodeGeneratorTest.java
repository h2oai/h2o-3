package water.codegen.java;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import hex.Model;
import water.Key;
import water.codegen.SBPrintStream;
import water.codegen.driver.DirectOutputDriver;

import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static water.codegen.java.JCodeGenUtil.klazz;

/**
 * jUnit tests to fix interface of MethodCodeGenerator.
 */
public class CodeGeneratorTest {

  @Test
  public void testMethodGenerator() throws IOException {
    MethodCodeGenerator m = MethodCodeGenerator.codegen("testA")
          .withModifiers(PUBLIC, STATIC)
          .withParams(String.class, "s");

    OutputStream os = new ByteArrayOutputStream();
    SBPrintStream sb = new SBPrintStream(os);

    ClassCodeGenerator c = klazz("KlazzAhoj")
        .withModifiers(PUBLIC)
        .withMethod(m);

    final CompilationUnitGenerator cu = CompilationUnitGenerator.codegen("water.ai.h2o", "KlazzAhoj").withClassGenerator(c);

    // Stdout
    try {
//      new DirectOutputDriver().codegen(model, System.out);
    } finally {
      // Nothing
    }

    /*
    // Zip output
    FileOutputStream fos = new FileOutputStream(new File("/tmp/testmodel.zip"));
    try {
      ZipOutputDriver.codegen(
          model,
          fos,
          new ZipInputStream(this.getClass().getResourceAsStream("/model-pojo/model-pojo.zip")),
          true);
    } finally {
      fos.close();
    }*/
  }

}
