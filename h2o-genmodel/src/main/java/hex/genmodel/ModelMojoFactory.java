package hex.genmodel;

import hex.genmodel.algos.deepwater.DeepwaterMojoReader;
import hex.genmodel.algos.drf.DrfMojoReader;
import hex.genmodel.algos.gbm.GbmMojoReader;
import hex.genmodel.algos.glm.GlmMojoReader;
import hex.genmodel.algos.glrm.GlrmMojoReader;
import hex.genmodel.algos.kmeans.KMeansMojoReader;
import hex.genmodel.algos.word2vec.Word2VecMojoReader;

import hex.genmodel.algos.xgboost.XGBoostMojoReader;

/**
 * Factory class for instantiating specific MojoGenmodel classes based on the algo name.
 */
public class ModelMojoFactory {

  public static ModelMojoReader getMojoReader(String algo) {
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

      case "XGBoost":
        return new XGBoostMojoReader();

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

      default:
        throw new IllegalStateException("Unsupported MOJO algorithm: " + algo);
    }
  }

}
