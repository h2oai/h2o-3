package hex.genmodel;

import hex.ModelCategory;
import hex.genmodel.descriptor.Table;
import hex.genmodel.descriptor.VariableImportances;

public interface ModelDescriptor {

  String[][] scoringDomains();

  String projectVersion();

  String algoName();

  String algoFullName();
  
  String offsetColumn();
  
  String weightsColumn();
  
  String foldColumn();

  ModelCategory getModelCategory();

  boolean isSupervised();

  int nfeatures();
  
  String[] features();

  int nclasses();

  String[] columnNames();

  boolean balanceClasses();

  double defaultThreshold();

  double[] priorClassDist();
  
  double[] modelClassDist();

  String uuid();

  String timestamp();
  
  VariableImportances variableImportances();
  
  Table modelSummary();

}
