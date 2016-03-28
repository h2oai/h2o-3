package water.codegen.java;

import java.util.ArrayList;

import hex.Model;
import hex.ModelMetrics;
import hex.genmodel.GenModel;
import hex.genmodel.annotations.CG;
import water.Key;
import water.codegen.CodeGenerator;

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static water.codegen.java.JCodeGenUtil.VALUE;
import static water.codegen.java.JCodeGenUtil.ctor;
import static water.codegen.java.JCodeGenUtil.field;
import static water.codegen.java.JCodeGenUtil.klazz;
import static water.codegen.java.JCodeGenUtil.s;
import static water.codegen.java.JCodeGenUtil.toJavaId;

/**
 * Model generator:
 *  -  can generates multiple classes (compilation units)
 *
 *
 *  - preview can just generate top-level compilation unit
 *
 *  FIXME: all methods field/method/class should be generalized and definable (ModelCodeGenerator
 *  shoudl accept their implementation)
 *  FIXME: why model container is not CodeGeneratorPipeline
 */
abstract public class ModelCodeGenerator<S extends ModelCodeGenerator<S, M>, M extends Model<M, ?, ?>> extends
                                                          ArrayList<CompilationUnitGenerator> implements ClassGenContainer {
  protected final M model;
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
    cug.setMcg(this);
    return self();
  }

  /** Create initial top-level model compilation unit. */
  protected CompilationUnitGenerator createModelCu() {
    return new CompilationUnitGenerator(packageName, getModelName())
        .withPackageImport("java.util.Map",
                           "hex.genmodel.GenModel",
                           "hex.genmodel.annotations.ModelPojo");
  }

  /** Create initial class generator
   * for model filling all fields by defaults.
   */
  protected ClassCodeGenerator createModelClass(CompilationUnitGenerator cucg) {
    // Build a model class generator by composing small pieces
    final String modelName = getModelName();
    // Create a klass generator and prepare method generators for all abstract class in GenModel
    ClassCodeGenerator ccg = klazz(modelName, GenModel.class, model)
        .withMixin(model, true, ModelMixin.class)
        .withModifiers(PUBLIC)
        .withAnnotation(s("@ModelPojo(name=\"").p(getModelName()).p("\", algorithm=\"").p(getAlgoName()).p("\")"))
        .withCtor(
            ctor(modelName) // FIXME: this is repeated and can be derived from context
              .withModifiers(PUBLIC) // FIXME: Adopt Scala strategy - everything is public if not denied
              .withBody(s("super(NAMES, DOMAINS);"))
        );

    if (model.isSupervised()) {
      ccg
          .withField(
              field(double[].class, "PRIOR_CLASS_DISTRIB")
                  .withComment("Prior class distribution")
                  .withModifiers(PUBLIC | STATIC | FINAL)
                  .withValue(s().pj(model._output._priorClassDist)),
              field(double[].class, "MODEL_CLASS_DISTRIB")
                  .withComment("Class distribution used for model building")
                  .withModifiers(PUBLIC | STATIC | FINAL)
                  .withValue(s().pj(model._output._modelClassDist))
          );
    }

    return ccg;
  }

  public final S build() {
    // Shared work by all generators
    CompilationUnitGenerator cug = createModelCu();
    ClassCodeGenerator cg = createModelClass(cug);

    // FIXME: reverse initialization order with setup link: cug->mcg a pak cg->cug
    this.withCompilationUnit(cug.withClassGenerator(cg));

    // Reimplement in model-specifc subclasss
    return buildImpl(cug, cg);
  }

  /**
   * The method which should be implemented by a corresponding model generator.
   *
   * @param cucg compilation unit generator
   * @param ccg  model class generator, it already predefines full model generation, but
   *             a corresponding model generator can redefine all slots.
   * @return  self
   */
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

  final public ClassGenContainer classContainer(CodeGenerator caller) {
    // FIXME this should be driven by a strategy
    // (1) forward here, (2) forward to encapsulating top-level class
    return this;
  }

  /* FIXME: how to define O ????
  public <O extends Model.Output> O modelOutput() {
    return model._output;
  }*/

  /** Create a new field generator */
  public FieldCodeGenerator FIELD(double[] fieldValue, String fieldName) {
    return JCodeGenUtil.field(double[].class, fieldName)
        .withModifiers(PUBLIC | STATIC | FINAL)
        .withValue(VALUE(fieldValue));
  }

  public FieldCodeGenerator FIELD(int[] fieldValue, String fieldName) {
    return JCodeGenUtil.field(int[].class, fieldName)
        .withModifiers(PUBLIC | STATIC | FINAL)
        .withValue(VALUE(fieldValue));



  }
}

/**
 * The mixin which define common model-POJO fields.
 */
class ModelMixin {

  @CG(delegate = "._output#nclasses", comment = "Number of output classes included in training data response column.")
  public static final int NCLASSES = -1;

  @CG(delegate = "._output._names", comment = "Names of features used by model training")
  public static final String[] NAMES = null;

  @CG(delegate = "._output._domains", comment = "Column domains. The last array contains domain of response column.")
  public static final String[][] DOMAINS = null;

  // FIXME remove to supervised ModelMixin !!!
  @CG(delegate = "._output._priorClassDist", comment = "Prior class distribution", when="#isSupervised")
  public static final double[] PRIOR_CLASS_DISTRIB = null;

  @CG(delegate = "._output._modelClassDist", comment = "Class distribution used for model building", when="#isSupervised")
  public static final double[] MODEL_CLASS_DISTRIB = null;
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


