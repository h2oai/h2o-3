package water.codegen.java;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import hex.Model;
import water.Key;
import water.codegen.SBPrintStream;
import water.util.FileUtils;

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
      //DirectOutputDriver.codegen(model, System.out);
    } finally {
      // Nothing
    }

    // Zip output
    FileOutputStream fos = new FileOutputStream(new File("/tmp/testmodel.zip"));
    try {
      ZipOutputDriver.codegen(
          model,
          fos,
          new ZipInputStream(this.getClass().getResourceAsStream("/model-pojo/model-pojo.zip")));
    } finally {
      fos.close();
    }
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

  public static void append(ZipInputStream zis, ZipOutputStream zos) throws IOException {
    ZipEntry ze = null;
    while ((ze = zis.getNextEntry()) != null) {
      ZipEntry zze = new ZipEntry(ze);
      zze.setCompressedSize(-1L);
      zos.putNextEntry(zze);
      if (!ze.isDirectory()) {
        FileUtils.copyStream(zis, zos);
      }
      zos.closeEntry();
    }
  }

  public static void codegen(ModelCodeGenerator<?, ?> mcg, OutputStream os, ZipInputStream zis) throws IOException {
    // Append content of existing zip file
    ZipOutputStream zos = new ZipOutputStream(os);
    if (zis != null) {
      try {
        append(zis, zos);
      } finally {
        FileUtils.close(zis);
      }
    }
    //
    try {
      for (int i = 0; i < mcg.size(); i++) {
        CompilationUnitGenerator cug = mcg.get(i);
        String name = cug.name + ".java";
        String packagePath = cug.packageName != null ? cug.packageName.replace('.', '/') : "";
        zos.putNextEntry(new ZipEntry("model-pojo/src/main/java/" + packagePath + "/" + name));
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
