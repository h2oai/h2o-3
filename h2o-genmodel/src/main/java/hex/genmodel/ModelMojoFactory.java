package hex.genmodel;

import hex.genmodel.algos.coxph.CoxPHMojoReader;
import hex.genmodel.algos.deeplearning.DeeplearningMojoReader;
import hex.genmodel.algos.drf.DrfMojoReader;
import hex.genmodel.algos.ensemble.StackedEnsembleMojoReader;
import hex.genmodel.algos.gam.GamMojoReader;
import hex.genmodel.algos.gbm.GbmMojoReader;
import hex.genmodel.algos.glm.GlmMojoReader;
import hex.genmodel.algos.isoforextended.ExtendedIsolationForestMojoReader;
import hex.genmodel.algos.pca.PCAMojoReader;
import hex.genmodel.algos.glrm.GlrmMojoReader;
import hex.genmodel.algos.isofor.IsolationForestMojoReader;
import hex.genmodel.algos.kmeans.KMeansMojoReader;
import hex.genmodel.algos.pipeline.MojoPipelineReader;
import hex.genmodel.algos.rulefit.RuleFitMojoReader;
import hex.genmodel.algos.svm.SvmMojoReader;
import hex.genmodel.algos.targetencoder.TargetEncoderMojoReader;
import hex.genmodel.algos.word2vec.Word2VecMojoReader;
import hex.genmodel.algos.klime.KLimeMojoReader;

import java.util.ServiceLoader;

/**
 * Factory class for instantiating specific MojoGenmodel classes based on the algo name.
 */
public class ModelMojoFactory {

  public final static ModelMojoFactory INSTANCE = new ModelMojoFactory();

  /** Service loader for model mojo readers.
   *
   * Based on JavaDoc of SPI: "Instances of this class are not safe for use by multiple concurrent threads." - all usages of the loader
   * are protected by synchronized block.
   */
  private final ServiceLoader<ModelMojoReader> loader;

  private ModelMojoFactory() {
    loader = ServiceLoader.load(ModelMojoReader.class);
  }

  private ModelMojoReader loadMojoReader(String algo) {
    assert algo != null : "Name of algorithm should be != null!";
    synchronized (loader) {
      loader.reload();
      for (ModelMojoReader mrb : loader) {
        if (algo.equals(mrb.getModelName())) {
          return mrb;
        }
      }
    }
    return null;
  }

  public ModelMojoReader getMojoReader(String algo) {
    if (algo == null)
      throw new IllegalArgumentException("Algorithm not specified.");

    switch (algo) {
      case "Distributed Random Forest":
        return new DrfMojoReader();

      case "Gradient Boosting Method":
      case "Gradient Boosting Machine":
        return new GbmMojoReader();
        
      case "Generalized Low Rank Modeling":
      case "Generalized Low Rank Model":
        return new GlrmMojoReader();

      case "Generalized Linear Modeling":
      case "Generalized Linear Model":
        return new GlmMojoReader();
      case "Generalized Additive Model":
        return new GamMojoReader();
      case "Word2Vec":
        return new Word2VecMojoReader();
        
      case "TargetEncoder":
        return new TargetEncoderMojoReader();

      case "Isolation Forest":
        return new IsolationForestMojoReader();

      case "Extended Isolation Forest":
        return new ExtendedIsolationForestMojoReader();

      case "K-means":
        return new KMeansMojoReader();

      case "Deep Learning":
      case "deep learning":
        return new DeeplearningMojoReader();

      case "Support Vector Machine (*Spark*)":
        return new SvmMojoReader();

      case "StackedEnsemble":
      case "Stacked Ensemble":
        return new StackedEnsembleMojoReader();
      
      case "k-LIME":
        return new KLimeMojoReader();

      case "MOJO Pipeline":
        return new MojoPipelineReader();
      case "Principal Components Analysis":
        return new PCAMojoReader();
      
      case "Cox Proportional Hazards":
        return new CoxPHMojoReader();

      case "RuleFit":
        return new RuleFitMojoReader(); 
        
      default:
        // Try to load MOJO reader via service
        ModelMojoReader mmr = loadMojoReader(algo);
        if (mmr != null) {
          return mmr;
        } else {
          throw new IllegalStateException("Algorithm `" + algo + "` is not supported by this version of h2o-genmodel. " +
                  "If you are using an algorithm implemented in an extension, be sure to include a jar dependency of the extension (eg.: ai.h2o:h2o-genmodel-ext-" + algo.toLowerCase() + ")");
        }
    }
  }
}
