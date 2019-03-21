package ai.h2o.automl.hpsearch;


public abstract class RFSMBO extends SMBO {
  
  private final AcquisitionFunction _acquisitionFunction;

  public RFSMBO(AcquisitionFunction acquisitionFunction) {
    _acquisitionFunction = acquisitionFunction;
  }

  /**
   * 
   * @param theBiggerTheBetter we need to specify according to our objective function whether we are minimizing or maximizing our metric
   */
  public RFSMBO(boolean theBiggerTheBetter) {
    _acquisitionFunction = new EI(0.0, theBiggerTheBetter);
  }

  @Override
  public SurrogateModel surrogateModel() {
    return new RFSurrogateModel();
  }

  @Override
  public AcquisitionFunction acquisitionFunction() {
    return _acquisitionFunction;
  }
}
