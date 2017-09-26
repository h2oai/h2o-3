package hex.genmodel;

import hex.genmodel.algos.deeplearning.DeeplearningMojoReader;
import hex.genmodel.algos.deepwater.DeepwaterMojoReader;
import hex.genmodel.algos.drf.DrfMojoReader;
import hex.genmodel.algos.gbm.GbmMojoReader;
import hex.genmodel.algos.glm.GlmMojoReader;
import hex.genmodel.algos.glrm.GlrmMojoReader;
import hex.genmodel.algos.kmeans.KMeansMojoReader;
import hex.genmodel.algos.svm.SvmMojoReader;
import hex.genmodel.algos.word2vec.Word2VecMojoReader;

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

      case "Deep Water":
        return new DeepwaterMojoReader();

      case "Generalized Low Rank Modeling":
      case "Generalized Low Rank Model":
        return new GlrmMojoReader();

      case "Generalized Linear Modeling":
      case "Generalized Linear Model":
        return new GlmMojoReader();

      case "Word2Vec":
        return new Word2VecMojoReader();

      case "K-means":
        return new KMeansMojoReader();

      case "Deep Learning":
      case "deep learning":
        return new DeeplearningMojoReader();

      case "Support Vector Machine (*Spark*)":
        return new SvmMojoReader();

      default:
        // Try to load MOJO reader via service
        ModelMojoReader mmr = loadMojoReader(algo);
        if (mmr != null) {
          return mmr;
        } else {
          throw new IllegalStateException("Unsupported MOJO algorithm: " + algo);
        }
    }
  }
}
