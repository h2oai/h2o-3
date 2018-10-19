package hex.genmodel;

import hex.ModelCategory;

public interface ModelDescriptor {

  String[][] scoringDomains();

  String projectVersion();

  String algoName();

  String algoFullName();

  ModelCategory getModelCategory();

  boolean isSupervised();

  int nfeatures();

  int nclasses();

  String[] columnNames();

  boolean balanceClasses();

  double defaultThreshold();

  double[] priorClassDist();

  double[] modelClassDist();

  String uuid();

  String timestamp();

}
