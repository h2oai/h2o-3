package ai.h2o.automl.hpsearch;


public abstract class RFSMBO extends SMBO {
  
  @Override
  public SurrogateModel surrogateModel() {
    return new RFSurrogateModel();
  }

  @Override
  public SMBOSelectionCreteria selectionCriteria() {
    return null;
  }
}
