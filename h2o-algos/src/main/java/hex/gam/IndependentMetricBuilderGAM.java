package hex.gam;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLMModel;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.MathUtils;

import static hex.glm.GLMModel.GLMParameters.Family.*;

public class IndependentMetricBuilderGAM extends ModelMetricsSupervised.IndependentMetricBuilderSupervised<IndependentMetricBuilderGAM> {
    double _residual_deviance;
    double _null_deviance;
    long _nobs;
    double _log_likelihood;
    double _aic;
    private double _aic2;
    final GLMModel.GLMWeightsFun _glmf;
    ModelMetrics.IndependentMetricBuilder _metricBuilder;  // point to generic model metric classes
    final boolean _intercept;
    private final double[] _ymu;
    final boolean _computeMetrics;
    final private int _rank;
    int _nclass;
    transient double[] _ds = new double[3];
    transient float[] _yact = new float[1];

    public IndependentMetricBuilderGAM(String[] domain, double[] ymu, GLMModel.GLMWeightsFun glmf, int rank, boolean computeMetrics, boolean intercept, int nclass, MultinomialAucType aucType, DistributionFamily distributionFamily) {
        super(domain==null?0:domain.length, domain);
        _intercept = intercept;
        _computeMetrics = computeMetrics;
        _glmf = glmf;
        _rank = rank;
        _nclass = nclass;
        _ymu = ymu;
        switch (_glmf._family) {
            case binomial:
            case quasibinomial:
            case fractionalbinomial:
                _metricBuilder = new ModelMetricsBinomial.IndependentMetricBuilderBinomial(domain, distributionFamily); break;
            case multinomial:
                _metricBuilder = new ModelMetricsMultinomial.IndependentMetricBuilderMultinomial(nclass, domain, aucType); break;
            case ordinal:
                _metricBuilder = new ModelMetricsOrdinal.IndependentMetricBuilderOrdinal(nclass, domain); break;
            default:
                _metricBuilder = new ModelMetricsRegression.IndependentMetricBuilderRegression(); // everything else goes back regression
        }
    }

    @Override
    public double[] perRow(double[] ds, float[] yact, double weight, double offset) {
        if (weight == 0) return ds;
        _metricBuilder.perRow(ds, yact, weight, offset); // grab the generic terms
        /* TODO
        if (_glmf._family.equals(GLMModel.GLMParameters.Family.negativebinomial))
            _log_likelihood += m.likelihood(weight, yact[0], ds[0]);
            
         */
        if (!ArrayUtils.hasNaNsOrInfs(ds) && !ArrayUtils.hasNaNsOrInfs(yact)) {
            if (_glmf._family.equals(GLMModel.GLMParameters.Family.multinomial) || _glmf._family.equals(GLMModel.GLMParameters.Family.ordinal))
                add2(yact[0], ds[0], weight, offset);
            else if (_glmf._family.equals(binomial) || _glmf._family.equals(quasibinomial) ||
                    _glmf._family.equals(fractionalbinomial))
                add2(yact[0], ds[2], weight, offset);
            else
                add2(yact[0], ds[0], weight, offset);
        }
        return ds;
    }

    private void add2(double yresp, double ypredict, double weight, double offset) {
        _wcount += weight;
        ++_nobs;
        if (!_glmf._family.equals(multinomial) && !_glmf._family.equals(ordinal)) {
            _residual_deviance += weight * _glmf.deviance(yresp, ypredict);
            if (offset == 0)
                _null_deviance += weight * _glmf.deviance(yresp, _ymu[0]);
            else
                _null_deviance += weight * _glmf.deviance(yresp, _glmf.linkInv(offset + _glmf.link(_ymu[0])));
        }
        if (_glmf._family.equals(poisson)) {  // AIC for poisson
            long y = Math.round(yresp);
            double logfactorial = MathUtils.logFactorial(y);
            _aic2 += weight*(yresp*Math.log(ypredict)-logfactorial-ypredict);
        }
    }

    public void reduce(IndependentMetricBuilderGAM other) {
        if (_computeMetrics)
            _metricBuilder.reduce(other._metricBuilder);
        _residual_deviance += other._residual_deviance;
        _null_deviance += other._null_deviance;
        if (_glmf._family.equals(negativebinomial))
            _log_likelihood += other._log_likelihood;
        _nobs += other._nobs;
        _aic2 += other._aic2;
        _wcount += other._wcount;
    }

    public final double residualDeviance() { return _residual_deviance;}
    public final long nullDOF() { return _nobs-(_intercept?1:0);}
    public final long resDOF() {
        if (_glmf._family.equals(ordinal))
            return _nobs-(_rank/(_nclasses-1)+_nclasses-2);
        else
            return _nobs-_rank;
    }

    protected void computeAIC(){
        _aic = 0;
        switch( _glmf._family) {
            case gaussian:
                _aic =  _nobs * (Math.log(_residual_deviance / _nobs * 2 * Math.PI) + 1) + 2;
                break;
            case quasibinomial:
            case fractionalbinomial:
            case binomial:
                _aic = _residual_deviance;
                break;
            case poisson:
                _aic = -2*_aic2;
                break; // AIC is set during the validation task
            case gamma:
                _aic = Double.NaN;
                break;
            case ordinal:
            case tweedie:
            case multinomial:
                _aic = Double.NaN;
                break;
            case negativebinomial:
                _aic = 2* _log_likelihood;
                break;
            default:
                assert false : "missing implementation for family " + _glmf._family;
        }
        _aic += 2*_rank;
    }

    @Override
    public double[] perRow(double[] ds, float[] yact) {
        return perRow(ds, yact, 1, 0);
    }

    @Override
    public ModelMetrics makeModelMetrics() {
        computeAIC();
        ModelMetrics mm=_metricBuilder.makeModelMetrics();
        if (_glmf._family.equals(GLMModel.GLMParameters.Family.binomial) || _glmf._family.equals(quasibinomial) ||
                _glmf._family.equals(fractionalbinomial)) {
            ModelMetricsBinomial metricsBinomial = (ModelMetricsBinomial) mm;
            mm = new ModelMetricsBinomialGLM(null, null, mm._nobs, mm._MSE, _domain, metricsBinomial._sigma,
                    metricsBinomial._auc, metricsBinomial._logloss, residualDeviance(), _null_deviance, _aic, nullDOF(),
                    resDOF(), null, _customMetric);
        } else if (_glmf._family.equals(multinomial)) {
            ModelMetricsMultinomial metricsMultinomial = (ModelMetricsMultinomial) mm;
            mm = new ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM(null, null, metricsMultinomial._nobs,
                    metricsMultinomial._MSE, metricsMultinomial._domain, metricsMultinomial._sigma, metricsMultinomial._cm,
                    metricsMultinomial._hit_ratios, metricsMultinomial._logloss, residualDeviance(),_null_deviance, _aic,
                    nullDOF(), resDOF(), metricsMultinomial._auc,  _customMetric);
        } else if (_glmf._family == GLMModel.GLMParameters.Family.ordinal) { // ordinal should have a different resDOF()
            ModelMetricsOrdinal metricsOrdinal = (ModelMetricsOrdinal) mm;
            mm = new ModelMetricsBinomialGLM.ModelMetricsOrdinalGLM(null, null, metricsOrdinal._nobs, metricsOrdinal._MSE,
                    metricsOrdinal._domain, metricsOrdinal._sigma, metricsOrdinal._cm, metricsOrdinal._hit_ratios,
                    metricsOrdinal._logloss, residualDeviance(), _null_deviance, _aic, nullDOF(), resDOF(), _customMetric);
        } else {
            ModelMetricsRegression metricsRegression = (ModelMetricsRegression) mm;
            mm = new ModelMetricsRegressionGLM(null, null, metricsRegression._nobs, metricsRegression._MSE,
                    metricsRegression._sigma, metricsRegression._mean_absolute_error,
                    metricsRegression._root_mean_squared_log_error, residualDeviance(),
                    residualDeviance() / _wcount, _null_deviance, _aic, nullDOF(), resDOF(), _customMetric);
        }
        return mm;
    }
}
