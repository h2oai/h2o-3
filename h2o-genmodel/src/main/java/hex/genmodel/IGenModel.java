package hex.genmodel;

import hex.ModelCategory;

import java.util.EnumSet;

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
  boolean isSupervised();

  /**
   * Returns number of input features.
   * @return number of input features used for training.
   */
  int nfeatures();

  /**
   * Returns number of output classes for classifiers or 1 for regression models. For unsupervised models returns 0.
   * @return returns number of output classes or 1 for regression models.
   */
  int nclasses();


  /** Returns this model category.
   *
   * @return model category
   * @see hex.ModelCategory
   */
  ModelCategory getModelCategory();

  /**
   * For models with multiple categories, returns the set of all supported categories.
   */
  EnumSet<ModelCategory> getModelCategories();
}
