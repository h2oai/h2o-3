package ai.h2o.automl.hpsearch;


public abstract class GPSMBO extends SMBO {
  
  private final AcquisitionFunction _acquisitionFunction;
  private final SurrogateModel _surrogateModel;

  public GPSMBO(AcquisitionFunction acquisitionFunction, SurrogateModel surrogateModel) {
    _acquisitionFunction = acquisitionFunction;
    _surrogateModel = surrogateModel;
  }

  /**
   * 
   * @param theBiggerTheBetter we need to specify according to our objective function whether we are minimizing or maximizing our metric
   */
  public GPSMBO(boolean theBiggerTheBetter) {
    _acquisitionFunction = new EI(0.0, theBiggerTheBetter);
    _surrogateModel = new GPSurrogateModel();
  }

  @Override
  public SurrogateModel surrogateModel() {
    return _surrogateModel;
  }

  @Override
  public AcquisitionFunction acquisitionFunction() {
    return _acquisitionFunction;
  }
}
