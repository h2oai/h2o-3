package water.codegen.java;

import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.codegen.JCodeSB;

import static water.codegen.util.ArrayUtils.append;

/**
 * FIXME: this is generator for top-level compilation unit:
 *
 */
public class CompilationUnitGenerator extends CodeGeneratorPipeline<CompilationUnitGenerator, ClassCodeGenerator> {

  /** Package where to generate the class. */
  public final String packageName;

  /* Imported packages */
  String[] importedPackages;

  /* Name of compilation unit - should be derived from top-level class. */
  public final String name;

  private CodeGenerator comment;

  private JavaCodeGenerator<?, ?> mcg;

  public static CompilationUnitGenerator codegen(String packageName, String name) {
    return new CompilationUnitGenerator(packageName, name);
  }

  public CompilationUnitGenerator(String packageName, String name) {
    this.packageName = packageName;
    this.name = name;
  }

  public CompilationUnitGenerator withPackageImport(String ... packageNames) {
    this.importedPackages = append(this.importedPackages, packageNames);
    return self();
  }

  public CompilationUnitGenerator withClassGenerator(ClassCodeGenerator... ccgs) {
    for (ClassCodeGenerator ccg : ccgs) {
      add(ccg);
      ccg.setCug(this);
    }
    return self();
  }

  public CompilationUnitGenerator withComment(CodeGenerator commentGenerator) {
    this.comment = commentGenerator;
    return self();
  }

  @Override
  public void generate(JCodeSB out) {
    // Top level comment (always generated for CUs)
    if (comment != null) {
      comment.generate(out);
    }

    // Package of CU
    if (packageName != null) {
      out.p("package ").p(packageName).p(';').nl(2);
    }

    // Imported packages
    if (importedPackages != null) {
      for (String importedPackage : importedPackages) {
        out.p("import ").p(importedPackage).p(';').nl();
      }
      out.nl(2);
    }

    // Generate defined types
    super.generate(out);
    // Put endline at the end of file
    out.nl();
  }

  @Override
  final public ClassGenContainer classContainer(CodeGenerator caller) {
    return mcg.classContainer(caller);
  }

  JavaCodeGenerator<?, ?> mcg() {
    return mcg;
  }

  void setMcg(JavaCodeGenerator<?, ?> mcg) {
    this.mcg = mcg;
  }

}
