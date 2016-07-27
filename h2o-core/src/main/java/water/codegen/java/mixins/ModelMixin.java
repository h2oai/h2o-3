package water.codegen.java.mixins;

import hex.genmodel.annotations.CG;

/**
 * The mixin which define common model-POJO generated fields.
 *
 * In the most of cases, the field generation is directly delegated
 * to a specified field/method of model.
 */
public class ModelMixin {

  @CG.Delegate(target = "._output#nclasses", comment = "Number of output classes included in training data response column.")
  public static final int NCLASSES = -1;

  @CG.Manual(comment = "Names of features used by model training")
  public static final String[] NAMES = null;

  @CG.Delegate(target = "._output._domains", comment = "Column domains. The last array contains domain of response column.")
  public static final String[][] DOMAINS = null;

  // FIXME remove to supervised ModelMixin !!!
  @CG.Delegate(target = "._output._priorClassDist", comment = "Prior class distribution")
  public static final double[] PRIOR_CLASS_DISTRIB = null;

  // FIXME remove to supervised ModelMixin !!!
  @CG.Delegate(target = "._output._modelClassDist", comment = "Class distribution used for model building")
  public static final double[] MODEL_CLASS_DISTRIB = null;

  // FIXME remove to supervised ModelMixin !!!
  @CG.Delegate(target = "._output#isClassifier")
  public static final boolean GEN_IS_CLASSIFIER = false;

  // FIXME remove to supervised ModelMixin !!!
  @CG.Delegate(target = "._parms._balance_classes")
  public static final boolean GEN_BALANCE_CLASSES = false;

  @CG.Delegate(target = "#defaultThreshold")
  public static final double DEFAULT_THRESHOLD = 0.0;
}
