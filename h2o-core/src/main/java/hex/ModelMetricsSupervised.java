package hex;

import com.google.gson.JsonObject;
import hex.genmodel.IMetricBuilder;
import hex.genmodel.MojoModel;
import hex.genmodel.attributes.ModelAttributes;
import hex.genmodel.utils.DistributionFamily;
import water.fvec.Chunk;
import water.fvec.Frame;

public class ModelMetricsSupervised extends ModelMetrics {
  public final String[] _domain;// Name of classes
  public final double _sigma;   // stddev of the response (if any)

  public ModelMetricsSupervised(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma, CustomMetric customMetric) {
    super(model, frame, nobs, mse, null, customMetric);
    _domain = domain;
    _sigma = sigma;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    return sb.toString();
  }

  public double r2() { // TODO: Override for GLM Regression  - create new Generic & Generic V3 versions
    double var = _sigma*_sigma;
    return 1.0-_MSE /var;
  }

  abstract public static class MetricBuilderSupervised<T extends MetricBuilderSupervised<T>> extends MetricBuilder<T> {
    protected final String[] _domain;
    protected final int _nclasses;

    public MetricBuilderSupervised(int nclasses, String[] domain) {
      _nclasses = nclasses;
      _domain = domain;
      _work = new double[_nclasses+1];
    }
  }

  abstract public static class IndependentMetricBuilderSupervised<T extends IndependentMetricBuilderSupervised<T>> extends IndependentMetricBuilder<T> {
    protected final String[] _domain;
    protected final int _nclasses;

    public IndependentMetricBuilderSupervised() {
      _domain = null;
      _nclasses = -1;
    }

    public IndependentMetricBuilderSupervised(int nclasses, String[] domain) {
      _nclasses = nclasses;
      _domain = domain;
      _work = new double[_nclasses+1];
    }
  }

  public static class SupervisedMetricBuilderFactory<TBinaryModel extends Model, TMojoModel extends MojoModel>
    extends MetricBuilderFactory<TBinaryModel, TMojoModel> {

    @Override
    public IMetricBuilder createBuilder(TMojoModel mojoModel, JsonObject extraInfo) {
      ModelAttributes attributes =  mojoModel._modelAttributes;
      String distributionFamilyString = (String)attributes.getParameterValueByName("distribution");
      DistributionFamily distributionFamily = DistributionFamily.valueOf(distributionFamilyString);
      String responseColumn = mojoModel._responseColumn;
      String[] responseDomain = mojoModel.getDomainValues(responseColumn);
      int numberOfClasses = mojoModel._nclasses;
      
      switch (mojoModel._category) {
        case Binomial:
          return new ModelMetricsBinomial.IndependentMetricBuilderBinomial(responseDomain, distributionFamily);
        case Multinomial:
          Object aucTypeObject = attributes.getParameterValueByName("auc_type");
          MultinomialAucType aucType = MultinomialAucType.NONE;
          if (aucTypeObject != null) {
            aucType = MultinomialAucType.valueOf((String)aucTypeObject);
          }
          return new ModelMetricsMultinomial.IndependentMetricBuilderMultinomial(numberOfClasses, responseDomain, aucType);
        case Regression:
          Distribution distribution = getDistribution(distributionFamily, attributes);
          return new ModelMetricsRegression.IndependentMetricBuilderRegression(distribution);
        case Ordinal:
          return new ModelMetricsOrdinal.IndependentMetricBuilderOrdinal(numberOfClasses, responseDomain);
        default:
          throw new RuntimeException(String.format("Model category {0} is not supported for supervised metric calculation.", mojoModel._category));
      }
    }
    
    private Distribution getDistribution(DistributionFamily distributionFamily, ModelAttributes attributes) {
      Model.Parameters genericParameters = new Model.Parameters() {
        @Override
        public String algoName() { return null; }

        @Override
        public String fullName() { return null; }

        @Override
        public String javaName() { return null; }

        @Override
        public long progressUnits() { return 0; }
      };

      genericParameters._distribution = distributionFamily;
      Object huberAlphaObject = attributes.getParameterValueByName("huberAlpha");
      if (huberAlphaObject != null) {
        genericParameters._huber_alpha = (Double)huberAlphaObject;
      }
      Object quantileAlphaObject = attributes.getParameterValueByName("quantileAlpha");
      if (quantileAlphaObject != null) {
        genericParameters._quantile_alpha = (Double)quantileAlphaObject;
      }
      Object tweediePowerObject = attributes.getParameterValueByName("tweediePower");
      if (tweediePowerObject != null) {
        genericParameters._tweedie_power = (Double)tweediePowerObject;
      }
      Object customDistributionFuncObject = attributes.getParameterValueByName("customDistributionFunc");
      if (customDistributionFuncObject != null) {
        genericParameters._custom_distribution_func = (String)customDistributionFuncObject;
      }
      Distribution distribution = DistributionFactory.getDistribution(genericParameters);
      return distribution;
    }
  }
}
