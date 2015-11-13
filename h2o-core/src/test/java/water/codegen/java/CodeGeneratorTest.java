package water.codegen.java;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import hex.Model;
import water.Key;
import water.codegen.SBPrintStream;
import water.util.FileUtils;

import static water.codegen.java.JCodeGenUtil.klazz;
import static java.lang.reflect.Modifier.*;

/**
 * jUnit tests to fix interface of MethodCodeGenerator.
 */
public class CodeGeneratorTest {

  @Test
  public void testMethodGenerator() throws IOException {
    MethodCodeGenerator m = MethodCodeGenerator.codegen("testA")
          .withModifiers(PUBLIC, STATIC)
          .withParams("String", "s");

    OutputStream os = new ByteArrayOutputStream();
    SBPrintStream sb = new SBPrintStream(os);

    ClassCodeGenerator c = klazz("KlazzAhoj")
        .withModifiers(PUBLIC)
        .withMethod(m);

    final CompilationUnitGenerator cu = CompilationUnitGenerator.codegen("water.ai.h2o", "KlazzAhoj").withClassGenerator(c);

    BlahModelCodeGenerator model = new BlahModelCodeGenerator(
        new BlahModel(Key.make("BlahModel#43"), new Model.Parameters() {
          @Override
          protected long nFoldSeed() {
            return 3;
          }
        }, new Model.Output() {{
          int len = 40;
          String[] sa = new String[len];
          for (int i = 0; i< len; i++) {
            sa[i] = "F" + i;
          }
          _names = sa;
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
      DirectOutputDriver.codegen(model, System.out);
    } finally {
      // Nothing
    }

    // Zip output
    /*
    FileOutputStream fos = new FileOutputStream(new File("/tmp/testmodel.zip"));
    try {
      ZipOutputDriver.codegen(
          model,
          fos);
    } finally {
      fos.close();
    }*/
  }

}

class DirectOutputDriver {
  public static void codegen(ModelCodeGenerator<?, ?> mcg, OutputStream os) throws IOException {
    SBPrintStream sbos = new SBPrintStream(os);
    try {
      for (int i = 0; i < mcg.size(); i++) {
        CompilationUnitGenerator cug = mcg.get(i);
        cug.generate(sbos);
      }
    } finally {
      // Do nothing, since owner of stream should close it
    }
  }
}

// This serializer which is driving sending of compilation units
// into output stream
// It can do:
//  - zipping
//  - sending all CU into stream as it is
//  - opening/closing OoutputStream for each CU
class ZipOutputDriver {
  public static void codegen(ModelCodeGenerator<?, ?> mcg, OutputStream os) throws IOException {
    ZipOutputStream zos = new ZipOutputStream(os);
    try {
      for (CompilationUnitGenerator cug : mcg) {
        String name = cug.name;
        zos.putNextEntry(new ZipEntry(cug.name + ".java"));
        SBPrintStream sbos = new SBPrintStream(zos);
        try {
          cug.generate(sbos);
          sbos.flush();
          // Do not close
        } finally {
          zos.closeEntry();
        }
      }
    } finally {
      FileUtils.close(zos);
    }
  }
}
