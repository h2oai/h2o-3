package hex.genmodel.descriptor;

import hex.ModelCategory;

/**
 * Internal structure providing basic information about a model. Used primarily, but no exclusively by MOJO pipeline.
 * Every MOJO is guaranteed to provide the information defined by this interface.
 */
public interface ModelDescriptor {

  /**
   * Domains of categorical features. For each feature, there is a record. If the feature is not categorical, the value is null.
   *
   * @return An array of {@link String} representing the complete domain of each feature. Null if the feature is not categorical.
   */
  String[][] scoringDomains();

  /**
   * E.g. "3.24.0.1"
   *
   * @return A {@link String} representing version of H2O Open Source Machine Learning platform project.
   */
  String projectVersion();

  /**
   * @return A string with human-readable shortcut of the algorithm enveloped by this MOJO. Never null.
   */
  String algoName();

  /**
   * @return A string with human-readable, full textual representation of the algorithm. Never null.
   */
  String algoFullName();

  /**
   * @return A {@link String} with the name of the offset column used. Null if there was no offset column used during training.
   */
  String offsetColumn();

  /**
   * @return A {@link String} with the name of the weights column used. Null if there were no weights applied to the dataset.
   */
  String weightsColumn();

  /**
   * @return A {@link String} with the name of the fold column used. Null of there was no folding by using a fold column as a key done.
   */
  String foldColumn();

  /**
   * Model's category.
   *
   * @return One of {@link ModelCategory} values. Never null.
   */
  ModelCategory getModelCategory();

  /**
   * @return True for supervised learning models, false for unsupervised.
   */
  boolean isSupervised();

  /**
   * @return An integer representing a total count of features used for training of the model.
   */
  int nfeatures();

  /**
   * @return An array {@link String} representing the names of the features used for model training.
   */
  String[] features();

  /**
   * @return Domain cardinality of the response column, Only meaningful if the model has a categorical response and the model is supervised.
   */
  int nclasses();

  /**
   * @return An array of {@link String} representing the column names in the model. The very last one is the response column name,
   * if the model has a response column.
   */
  String[] columnNames();

  boolean balanceClasses();

  /**
   * Default threshold for assigning class labels to the target class. Applicable to binomial models only.
   *
   * @return A double primitive type with the default threshold value
   */
  double defaultThreshold();

  double[] priorClassDist();

  double[] modelClassDist();

  String uuid();

  String timestamp();
  
}
