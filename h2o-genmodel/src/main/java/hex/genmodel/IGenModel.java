package hex.genmodel;

import hex.ModelCategory;

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
  public boolean isSupervised();

  /**
   * Returns number of input features.
   * @return number of input features used for training.
   */
  public int nfeatures();

  /**
   * Returns number of output classes for classifiers or 1 for regression models.
   * @return returns number of output classes or 1 for regression models.
   */
  public int nclasses();


  /** Returns this model category.
   *
   * @return model category
   * @see hex.ModelCategory
   */
  public ModelCategory getModelCategory();
}
