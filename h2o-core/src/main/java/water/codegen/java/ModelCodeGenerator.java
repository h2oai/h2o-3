package water.codegen.java;

import java.util.ArrayList;

import hex.Model;
import hex.ModelMetrics;
import hex.genmodel.GenModel;
import water.Key;

import static water.codegen.java.JCodeGenUtil.ctor;
import static water.codegen.java.JCodeGenUtil.field;
import static water.codegen.java.JCodeGenUtil.larray;
import static water.codegen.java.JCodeGenUtil.method;
import static water.codegen.java.JCodeGenUtil.s;
import static water.codegen.java.JCodeGenUtil.toJavaId;
import static java.lang.reflect.Modifier.*;

/**
 * Model generator:
 *  -  can generates multiple classes (compilation units)
 *
 *
 *  - preview can just generate top-level compilation unit
 *
 *  FIXME: all methods field/method/class should be generalized and definable (ModelCodeGenerator
 *  shoudl accept their implementation)
 */
abstract class ModelCodeGenerator<S extends ModelCodeGenerator<S, M>, M extends Model<M, ?, ?>> extends
                                                          ArrayList<CompilationUnitGenerator> implements ClassGenContainer {
  private final M model;
  private String packageName;

  protected ModelCodeGenerator(M model) {
    this.model = model;
  }

  final protected S self() {
    return (S) this;
  }

  public S withPackage(String packageName) {
    this.packageName = packageName;
    return self();
  }

  public S withCompilationUnit(CompilationUnitGenerator cug) {
    add(cug);
    return self();
  }

  /** Create initial top-level model compilation unit. */
  protected CompilationUnitGenerator createModelCu() {
    return new CompilationUnitGenerator(packageName, getModelName())
        .withPackageImport("java.util.Map",
                           "hex.genmodel.GenModel",
                           "hex.genmodel.annotations.ModelPojo");
  }

  /** Create initial class generator for model. */
  protected ClassCodeGenerator createModelClass(CompilationUnitGenerator cucg) {
    // Build a model class generator by composing small pieces
    final String modelName = getModelName();
    return new ClassCodeGenerator(modelName)
        .withModifiers(PUBLIC)
        .withExtend(GenModel.class)
        .withAnnotation(s("@ModelPojo(name=\"").p(getModelName()).p("\", algorithm=\"").p(getAlgoName()).p("\")"))
        .withCtor(
            ctor(modelName) // FIXME: this is repeated and can be derived from context
              .withModifiers(PUBLIC) // FIXME: Adopt Scala strategy - everything is public if not denied
              .withBody(s("super(NAMES, DOMAINS);"))
        )
        .withMethod(
            method("getModelCategory")
              .withModifiers(PUBLIC)
              .withReturnType(hex.ModelCategory.class)
              .withBody(s("return hex.ModelCategory.").p( model._output.getModelCategory()).p( ";"))
        )
        .withMethod(
            method("getUUID")
              .withModifiers(PUBLIC)
              .withReturnType(String.class)
              .withBody(s("return Long.toString(").pj(model.checksum()).p(");"))
        )
        .withMethod( // FIXME: need to be defined by actual model generator
            method("score0")
              .withOverride(true)
              .withBody(s("return pred"))
        )
        .withField( // FIXME: be more domain-specific, derive field type for value passed.
            field("int", "NCLASSES")
              .withComment("Number of output classes included in training data response column.")
              .withModifiers(PUBLIC | FINAL | STATIC)
              .withValue(s().pj(model._output.nclasses()))
        )
        .withField(
            field("String[]", "NAMES")
              .withComment("Names of features used by model training")
              .withModifiers(PUBLIC | STATIC | FINAL)
              .withValue(
                  larray(getModelName() + "_ColumnNames", model._output._names, 0, model._output.nfeatures())
                      .withClassContainer(this)
                      .withType(String.class))
        )
        .withField(
            field("String[][]", "DOMAINS")
                .withComment("Column domains. The last array contains domain of response column.")
                .withModifiers(PUBLIC | STATIC | FINAL)
                .withValue(
                    larray(getModelName() + "_ColumnInfo", model._output._domains)
                        .withClassContainer(this)
                        .withType(String.class))
        )
        .withField(
            field("double[]", "PRIOR_CLASS_DISTRIB")
                .withComment("Prior class distribution")
                .withModifiers(PUBLIC | STATIC | FINAL)
                .withValue(s().pj(model._output._priorClassDist)))
        .withField(
            field("double[]", "MODEL_CLASS_DISTRIB")
                .withComment("Class distribution used for model building")
                .withModifiers(PUBLIC | STATIC | FINAL)
                .withValue(s().pj(model._output._modelClassDist))
        );
  }

  public final S build() {
    // Shared work by all generators
    CompilationUnitGenerator cug = createModelCu();
    ClassCodeGenerator cg = createModelClass(cug);

    this.withCompilationUnit(cug.withClassGenerator(cg));

    // Reimplement in model-specifc subclasss
    return buildImpl(cug, cg);
  }

  abstract protected S buildImpl(CompilationUnitGenerator cucg, ClassCodeGenerator ccg);

  String getModelName() {
    return toJavaId(model._key.toString());
  }

  String getAlgoName() {
    return model.getClass().getSimpleName().toLowerCase().replace("model", "");
  }

  @Override
  public void add(ClassCodeGenerator ccg) {
    CompilationUnitGenerator cu = new CompilationUnitGenerator(this.packageName, ccg.name);
    ccg.modifiers &= ~(STATIC | PRIVATE); // Remove illegal modifiers from top-level classes
    cu.withClassGenerator(ccg);
    withCompilationUnit(cu);
  }
}

class BlahModelCodeGenerator extends ModelCodeGenerator<BlahModelCodeGenerator, BlahModel> {

  BlahModelCodeGenerator(BlahModel model) {
    super(model);
  }

  @Override
  protected BlahModelCodeGenerator buildImpl(CompilationUnitGenerator cucg,
                                             ClassCodeGenerator ccg) {
    return self();
  }
}

//
// Foo model
//
class BlahModel extends Model<BlahModel, Model.Parameters, Model.Output> {

  /**
   * Full constructor
   */
  public BlahModel(Key selfKey, Parameters parms, Output output) {
    super(selfKey, parms, output);
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return null;
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    return new double[0];
  }
}


