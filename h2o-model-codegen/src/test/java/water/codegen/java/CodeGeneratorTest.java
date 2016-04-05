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

    BlahModelCodeGenerator model = new BlahModelCodeGenerator(
        new BlahModel(Key.make("BlahModel#43"), new Model.Parameters() {
          @Override
          public String algoName() {
            return "Blah";
          }

          @Override
          public String fullName() {
            return "BlahALgo";
          }

          @Override
          public String javaName() {
            return "java.Blah";
          }

          @Override
          protected long nFoldSeed() {
            return 3;
          }

          @Override
          public long progressUnits() {
            return 0;
          }
        }, new Model.Output() {{
          int len = 40;
          String[] sa = new String[len];
          for (int i = 0; i< len; i++) {
            sa[i] = "F" + i;
          }
          _names = sa;

          int domainLen = 40;
          String dom[][] = new String[len][domainLen];
          for (int i = 0; i < len; i++) {
            for (int j = 0; j < domainLen; j++) {
              dom[i][j] = "DOMAIN_" + i + "_" + j;
            }
          }
          _domains = dom;
        }
          @Override
          public boolean isSupervised() {
            return true;
          }

          @Override
          public String[] classNames() {
            int len = 40;
            String[] sa = new String[len];
            for (int i = 0; i< len; i++) {
              sa[i] = "S" + i;
            }
            return sa;
          }
        })).withPackage("ai.h2o.codegen").build();

    // Stdout
    try {
      new DirectOutputDriver().codegen(model, System.out);
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
