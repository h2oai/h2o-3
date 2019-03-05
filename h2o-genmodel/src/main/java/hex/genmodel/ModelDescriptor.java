package hex.genmodel;

import hex.ModelCategory;
import hex.genmodel.descriptor.VariableImportances;

//TODO: Do we really want our users to implement all of this, even if most of it remains null ?
// Is there other way to point out the really necessary stuff ? E.g. a child interface extending ModelDescriptor
// with all the details ?
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

  int nclasses();

  String[] columnNames();

  boolean balanceClasses();

  double defaultThreshold();

  double[] priorClassDist();

  double[] modelClassDist();

  String uuid();

  String timestamp();
  
  VariableImportances variableImportances();

}
