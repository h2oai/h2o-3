package water.codegen.driver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import water.H2O;
import water.codegen.SBPrintStream;
import water.codegen.java.CompilationUnitGenerator;
import water.codegen.java.ModelCodeGenerator;
import water.util.FileUtils;
import water.util.Log;

/**
 * Created by michal on 3/22/16.
 */
// This serializer which is driving sending of compilation units
// into output stream
// It can do:
//  - zipping
//  - sending all CU into stream as it is
//  - opening/closing OoutputStream for each CU
public class ZipOutputDriver extends CodeGenDriver {

  // FIXME: allow for renaming top-level project directory "model-pojo"
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

  public static void codegen(ModelCodeGenerator<?, ?> mcg, OutputStream os, ZipInputStream zis, boolean appendGenModelLib) throws IOException {
    // Append content of existing zip file
    ZipOutputStream zos = new ZipOutputStream(os);
    if (zis != null) {
      try {
        append(zis, zos);
      } finally {
        FileUtils.close(zis);
      }
    }

    // Append meta information about H2O
    // FIXME: should be already prepared in ZIS input variable
    appendMetaInfo(zos, appendGenModelLib);
    // Generate model code
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

  private static void appendMetaInfo(ZipOutputStream zos, boolean appendGenModelLib) throws IOException {
    zos.putNextEntry(new ZipEntry("model-pojo/gradle.properties"));
    SBPrintStream sbos = new SBPrintStream(zos);
    try {
      sbos.p("h2oMajorName=").p(H2O.ABV.branchName()).nl();
      sbos.p("h2oMajorVersion=").p(H2O.ABV.projectVersion()).nl();
      sbos.p("h2oBuild=").p(H2O.ABV.buildNumber()).nl();
      if (appendGenModelLib) {
        sbos.p("hasGenModelLib=true").nl();
      }
    } finally {
      zos.closeEntry();
    }

    // Append gen-model.jar
    if (appendGenModelLib) {
      InputStream is = ZipOutputDriver.class.getResourceAsStream("/www/3/h2o-genmodel.jar");
      if (is != null) {
        try {
          zos.putNextEntry(new ZipEntry("model-pojo/lib/h2o-genmodel.jar"));
          FileUtils.copyStream(is, zos);
        } finally {
          zos.closeEntry();
          FileUtils.close(is);
        }
      } else {
        Log.warn("Cannot load h2o-genmodel.jar from class resources!");
      }
    }
  }

  @Override
  public void codegen(ModelCodeGenerator<?, ?> mcg, OutputStream os) throws IOException {
    codegen(mcg,
            os,
            new ZipInputStream(this.getClass().getResourceAsStream("/model-pojo/model-pojo.zip")),
            true);
  }
}