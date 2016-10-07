package hex.genmodel;

import hex.genmodel.algos.DrfMojoReader;
import hex.genmodel.algos.GbmMojoReader;

import java.io.IOException;

/**
 * Factory class for instantiating specific MojoGenmodel classes based on the algo name.
 */
public class ModelMojoFactory {

  public static ModelMojoReader getMojoReader(String algo) throws IOException {
    if (algo == null)
      throw new IOException("Unable to find information about the model's algorithm.");

    switch (algo) {
      case "Distributed Random Forest":
        return new DrfMojoReader();

      case "Gradient Boosting Method":
      case "Gradient Boosting Machine":
        return new GbmMojoReader();

      default:
        throw new IOException("Unsupported MOJO algorithm: " + algo);
    }
  }

}
