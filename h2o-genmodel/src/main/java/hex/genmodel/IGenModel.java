package hex.genmodel;

import hex.ModelCategory;
import hex.genmodel.annotations.CG;

/**
 * Interface publishing methods for generated models.
 *
 * This interface extend the original interface from H2O.
 */
public interface IGenModel {

  /**
   * Returns true for supervised models.
   * @return true if this class represents supervised model.
   */
  @CG(delegate="#isSupervised")
  boolean isSupervised();

  /**
   * Returns number of input features.
   * @return number of input features used for training.
   */
  @CG(delegate="._output#nfeatures")
  int nfeatures();

  /**
   * Returns number of output classes for classifiers or 1 for regression models.
   * @return returns number of output classes or 1 for regression models.
   */
  @CG(delegate="._output#nclasses")
  int nclasses();


  /** Returns this model category.
   *
   * @return model category
   * @see hex.ModelCategory
   */
  @CG(delegate="._output#getModelCategory")
  ModelCategory getModelCategory();
}
