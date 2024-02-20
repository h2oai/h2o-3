package hex.glm;

import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.fp.Function2;
import water.util.fp.Function3;

import static hex.glm.TweedieEstimator.LikelihoodEstimator.gamma;
import static hex.glm.TweedieEstimator.LikelihoodEstimator.*;
import static java.lang.Math.*;
import static org.apache.commons.math3.special.Gamma.*;

@SuppressWarnings({"SuspiciousNameCombination", "SameParameterValue"})
public class TweedieEstimator extends MRTask<TweedieEstimator> {

    private final long _max_iter_cnt;
    double _loglikelihood;
    double _llhDp;
    double _llhDpDp;
    final double _p;
    final double _phi;
    private final double _pp;
    private final double _ppp;
    private final double _p1;
    private final double _p2;
    private final double _p1sq;
    private final double _p2sq;
    private final double _invp1;
    private final double _invp1sq;
    private final double _invp2;
    private final double _invp2sq;
    private final double _logp1;
    private final double _logp2;
    private final double _log_phi;
    private final double _alpha;
    private final double _logDenom_p;
    private final double _logPhip1inv;
    private final double _pialpha;
    private final double _logInvDenom2ConstPart;
    private final double _pisq;
    private final double _epsilon = 1e-12; // used as a stopping criterion for series method when gradient and hessian are required
    private double _wSum, _wDpSum, _wDpDpSum, _logWMax;
    double _logVSum, _logVDpSum, _logVDpDpSum, _logVMax;
    private double _vDpSumSgn, _vDpDpSumSgn;
    private final boolean _useSaddlepoint;
    private final boolean _needDp;
    private final boolean _needDpDp;
    private final boolean _forceInversion;
    private final boolean _skipZerosIfVarPowerGT2;
    public long _skippedRows;
    public long _totalRows;
    
    enum LikelihoodEstimator {
        series,
        inversion,
        saddlepoint,
        gamma,
        poisson,
        invGaussian
    }

    public LikelihoodEstimator _method;

    TweedieEstimator(double variancePower, double dispersion) {
        this(variancePower, dispersion, false, false, false, false, false);
    }

    TweedieEstimator(double variancePower, double dispersion, boolean forceInversion) {
        this(variancePower, dispersion, false, false, false, forceInversion, false);
    }

    TweedieEstimator(double variancePower, double dispersion, boolean useSaddlepointApprox, boolean needDp,
                     boolean needDpDp, boolean forceInversion) {
        this(variancePower, dispersion, useSaddlepointApprox, needDp, needDpDp, forceInversion, false);
    }
    
    TweedieEstimator(double variancePower, double dispersion, boolean useSaddlepointApprox, boolean needDp,
                     boolean needDpDp, boolean forceInversion, boolean skipZerosIfVarPowerGT2) {
        assert variancePower >= 1 : "Tweedie variance power has to be greater than 1!";
        assert (forceInversion || useSaddlepointApprox) && !(needDp || needDpDp) || !forceInversion || !useSaddlepointApprox;
        _p = variancePower;
        _phi = dispersion;
        _max_iter_cnt = 25000;

        _pp = _p * _p;
        _ppp = _pp * _p;
        _p1 = (_p - 1);
        _p2 = (_p - 2);
        _p1sq = _p1 * _p1;
        _p2sq = _p2 * _p2;
        _invp1 = 1 / _p1;
        _invp1sq = _invp1 * _invp1;
        _invp2 = 1 / _p2;
        _invp2sq = _invp2 * _invp2;
        _logp1 = log(_p1);
        _logp2 = log(abs(_p2));
        _log_phi = log(_phi);
        _alpha = _p2 / _p1;
        _logDenom_p = (2 * _p * _invp1 + 4) * _logp1 + 2 * _logp2;
        _logPhip1inv = -_invp1 * _log_phi;
        _pialpha = -Math.PI * _alpha;
        _logInvDenom2ConstPart = -4 * _logp1 - 2 * _logp2;
        _pisq = Math.PI * Math.PI;

        _useSaddlepoint = useSaddlepointApprox;
        _needDp = needDp;
        _needDpDp = needDpDp;
        _forceInversion = forceInversion; // useful when bracketing close to p=2
        _skipZerosIfVarPowerGT2 = skipZerosIfVarPowerGT2;
    }


    public double logLikelihood(double y, double mu) {
        return logLikelihood(y, mu, 1);
    }

    public double logLikelihood(double y, double mu, double w) {
        return logLikelihood(y, mu, w, false);
    }

    public static double logLikelihood(double y, double mu, double p, double phi) {
        TweedieEstimator tweedieVariancePowerMLEstimator = new TweedieEstimator(p, phi);
        return tweedieVariancePowerMLEstimator.logLikelihood(y, mu);
    }

    public static double deviance(double y, double mu, double p) {
        double dev;
        if (p == 1) {
            if (y != 0)
                dev = y * log(y / mu) - (y - mu);
            else
                dev = mu;
        } else {
            if (p == 2) {
                dev = log(mu / y) + (y / mu) - 1;
            } else {
                if (p == 0) {
                    dev = pow(y - mu, 2);
                    dev = dev / 2;
                } else {
                    dev = pow(y, 2-p) / ((p-1) * (p-2)) + (y * pow(mu, 1-p)) / (p-1) - pow(mu, 2-p) / (p-2);
                }
            }
        }
        return 2 * dev;
    }

    private double gammaLLH(double y, double mu, double w) {
        final double a = 1 / _phi;
        final double b = 1 / (_phi * mu);
        if (y == 0) return Double.NEGATIVE_INFINITY;
        return w * (a * log(b) - logGamma(a) + log(y) * (a - 1) + (-b * y));
    }

    private double poissonLLH(double y, double mu, double w) {
        final double lambda = mu / _phi;
        if (abs(y / _phi - Math.round(y / _phi)) > _epsilon)
            return 0;
        return w * (y / _phi * log(lambda) - logGamma((y + 1) / _phi) - lambda);
    }

    private double invGaussLLH(double y, double mu, double w) {
        if (y == 0) return Double.NEGATIVE_INFINITY;
        y = y / mu;
        double dispersion = _phi * mu;
        return w * ((-log(dispersion) - log(2 * PI) - 3 * log(y) - pow(y - 1, 2) / dispersion / y) / 2 - log(mu));
    }


    private void accumulate(double llh, double grad, double hess) {
        _loglikelihood += llh;
        if (Double.isFinite(grad))
            _llhDp += grad;
        if (Double.isFinite(hess))
            _llhDpDp += hess;
    }

    private double logLikelihood(double y, double mu, double w, boolean accumulate) {
        if (w == 0) return 0;
        if (_p >= 2 && y <= 0) {
            if (accumulate && !_skipZerosIfVarPowerGT2) accumulate(Double.NEGATIVE_INFINITY, 0, 0);
            return Double.NEGATIVE_INFINITY;
        }
        double[] llh_llhDp_llhDpDp = MemoryManager.malloc8d(3);
        if (!_useSaddlepoint) {
            _method = series;
            final double xi = _phi / pow(y, 2 - _p);
            // decide whether we want the Fourier inversion approach
            if (_p == 1) {
                llh_llhDp_llhDpDp[0] = poissonLLH(y, mu, w);
                _method = poisson;
            } else if (_p == 2) {
                llh_llhDp_llhDpDp[0] = gammaLLH(y, mu, w);
                _method = gamma;
            } else if (_p == 3) {
                llh_llhDp_llhDpDp[0] = invGaussLLH(y, mu, w);
                _method = invGaussian;
            } else if (_p < 2) {
                // this "xi-based" heuristic is proposed in section 8 in
                // DUNN, Peter and SMYTH, Gordon, 2008. Evaluation of Tweedie exponential dispersion model densities by Fourier inversion.
                if (xi <= 0.01)
                    _method = inversion;
            } else if (_p > 2) {
                if (xi <= 1.0)
                    _method = inversion;
            }
            if (_forceInversion)
                _method = inversion;
            if (series.equals(_method))
                tweedieSeries(y, mu, w, llh_llhDp_llhDpDp);
            if ((inversion.equals(_method) || Double.isNaN(llh_llhDp_llhDpDp[0])) && _p != 1 && _p != 2) {
                llh_llhDp_llhDpDp[0] = tweedieInversion(y, mu, w);
                _method = inversion;
                if (!Double.isFinite(llh_llhDp_llhDpDp[0])) {
                    tweedieSeries(y, mu, w, llh_llhDp_llhDpDp);
                    _method = series;
                } else if (_needDp || _needDpDp) {
                    final double llh = llh_llhDp_llhDpDp[0];
                    tweedieSeries(y, mu, w, llh_llhDp_llhDpDp);
                    llh_llhDp_llhDpDp[0] = llh;
                }
            }
        }
        // Use saddlepoint approx. if the series method failed. See [1] for description and comparison of the
        // saddlepoint approximation.
        // [1] DUNN, Peter and SMYTH, Gordon, 2001. Tweedie Family Densities: Methods of Evaluation
        if (_useSaddlepoint || (Double.isNaN(llh_llhDp_llhDpDp[0]))) {
            // Try to use Saddlepoint approximation
            _method = saddlepoint;
            if (y == 0) {
                if (_p > 1 && _p < 2)
                    llh_llhDp_llhDpDp[0] = pow(mu, 2 - _p) / (_phi * (2 - _p));
                else
                    llh_llhDp_llhDpDp[0] = Double.NEGATIVE_INFINITY;
            } else {
                double dev = deviance(mu, y, _p);
                if (_p < 2) y += 1. / 6.;
                llh_llhDp_llhDpDp[0] = -0.5 * (log(2 * PI * _phi) + _p * log(y)) + (-dev / (2 * _phi));
            }
        }
        if (accumulate) accumulate(llh_llhDp_llhDpDp[0], llh_llhDp_llhDpDp[1], llh_llhDp_llhDpDp[2]);
        return llh_llhDp_llhDpDp[0];
    }


    private void tweedieSeries(double y, double mu, double w, double[] out_llh_dp_dpdp) {
        out_llh_dp_dpdp[0] = 0; // llh
        out_llh_dp_dpdp[1] = 0; // llhDp - gradient with respect to p
        out_llh_dp_dpdp[2] = 0; // llhDpDp - hessian with respect to p
        double llhDpPart = (pow(mu, -_p1) * y * log(mu) * _invp1 - pow(mu, -_p2) * log(mu) * _invp2 +
                pow(mu, -_p1) * y * _invp1sq - pow(mu, -_p2) * _invp2sq) * w / _phi;
        double llhDpDpPart = -(pow(mu, -_p1) * y * pow(log(mu), 2) * _invp1 - pow(mu, -_p2) * pow(log(mu), 2) * _invp2 +
                2 * (pow(mu, -_p1) * y * log(mu) * _invp1sq - pow(mu, -_p2) * log(mu) * _invp2sq +
                        pow(mu, -_p1) * y * _invp1sq * _invp1 - pow(mu, -_p2) * _invp2sq * _invp2)) * w / _phi;
        if (_p < 2) {
            if (y == 0) {
                out_llh_dp_dpdp[0] = -w * pow(mu, 2 - _p) / (_phi * (2 - _p));
                if (_needDp)
                    out_llh_dp_dpdp[1] = -(pow(mu, -_p2) * w * log(mu) * _invp2 + pow(mu, -_p2) * w * _invp2sq) / _phi;
                if (_needDpDp)
                    out_llh_dp_dpdp[2] = (pow(mu, -_p2) * w * pow(log(mu), 2) * _invp2 + 2 * pow(mu, -_p2) * w * log(mu) * _invp2sq + 2 * pow(mu, -_p2) * w * _invp2sq * _invp2) / _phi;
            } else {
                calculateWjSums(y, w);
                out_llh_dp_dpdp[0] = -log(y) + log(_wSum) + _logWMax - w * (pow(mu, -_p1) * y * _invp1 - pow(mu, -_p2) / _p2) / _phi;
                if (out_llh_dp_dpdp[0] == Double.POSITIVE_INFINITY) // can happen with p approaching one (wSum == Inf)
                    out_llh_dp_dpdp[0] = Double.NEGATIVE_INFINITY;
                if (_needDp)
                    out_llh_dp_dpdp[1] = llhDpPart + _wDpSum / _wSum;
                if (_needDpDp)
                    out_llh_dp_dpdp[2] = llhDpDpPart + (_wDpDpSum / _wSum - _wDpSum / _wSum * _wDpSum / _wSum);
            }
        } else { // _p > 2
            //mu = max(1e-16, mu); // no mass at 0
            if (y == 0) return;
            calculateVkSums(y, w);
            out_llh_dp_dpdp[0] = -log(PI * y) + _logVSum + _logVMax - w * (pow(mu, -_p1) * y * _invp1 - pow(mu, -_p2) / _p2) / _phi;
            if (_needDp)
                out_llh_dp_dpdp[1] = llhDpPart + exp(_logVDpSum - _logVSum) * _vDpSumSgn;
            if (_needDpDp)
                out_llh_dp_dpdp[2] = llhDpDpPart + (exp(_logVDpDpSum - _logVSum) * _vDpDpSumSgn - exp(_logVDpSum - _logVSum + _logVDpSum - _logVSum));
        }
    }

    public TweedieEstimator compute(Vec mu, Vec y, Vec weights) {
        if (_p >= 2 && y.min() <= 0 && !_skipZerosIfVarPowerGT2) {
            _loglikelihood = Double.NEGATIVE_INFINITY;
            _llhDp = 0;
            _llhDpDp = 0;
            return this;
        }
        return doAll(mu, y, weights);
    }

    @Override
    public void map(Chunk[] cs) {
        double mu, y, w, llh;
        _totalRows += cs[0]._len;
        // cs = {mu, response, weight}
        for (int i = 0; i < cs[0]._len; i++) {
            mu = max(0, cs[0].atd(i)); // In first iteration it sometimes generates negative responses
            if (!Double.isFinite(mu)) {
                _skippedRows++;
                continue;
            }

            y = cs[1].atd(i);
            w = cs[2].atd(i);
            llh = logLikelihood(y, mu, w, true);
            if (llh == 0 || !Double.isFinite(llh)) _skippedRows++;
            if (!Double.isFinite(llh)) {
                if ((!_needDp || !Double.isFinite(_llhDp)) && (!_needDpDp || !Double.isFinite(_llhDpDp))) {
                    _skippedRows += cs[0]._len - i;
                    return;
                }
            }
            // early stop if bestllh > current llh
        }
    }

    @Override
    public void reduce(TweedieEstimator mrt) {
        _loglikelihood += mrt._loglikelihood;
        _llhDp += mrt._llhDp;
        _llhDpDp += mrt._llhDpDp;
        _skippedRows += mrt._skippedRows;
        _totalRows += mrt._totalRows;
    }

    void cleanSums() {
        _logVSum = 0;
        _logVDpSum = 0;
        _logVDpDpSum = 0;
        _logVMax = 0;
        _logWMax = 0;
        _wSum = 0;
        _wDpSum = 0;
        _wDpDpSum = 0;
    }

    void calculateWjSums(double y, double w) {
        assert y > 0;
        final double negwy = (-1) * pow(w, _invp1) * pow(y, 2 * _invp1);
        final double denom_W_dp_dp_exped = pow(_p1, 2 * _invp1) * _p2 * pow(_phi, _invp1) * pow(y, _p * _invp1);
        final double log_p1_phi_wy = log(_p1 * _phi / (w * y));
        final double logs_sumWdp = -(2 - log(_p1 * _phi / (w * y))) * _p + 3 + 2 * log(w * y / (_p1 * _phi));
        final double p1alphaw = pow(_p1, _alpha) * pow(w, _invp1);
        final double ps_sumWdp = _p1sq * _p2;
        final double pphiy_sumWdp = -_p2 * pow(_phi, _invp1) * pow(y, _alpha);
        final double log_y = log(y);
        final double log_w = log(w);

        boolean WjLB = false;
        boolean WjUB = false;
        boolean WjDpLB = !_needDp;
        boolean WjDpUB = !_needDp;
        boolean WjDpDpLB = !_needDpDp;
        boolean WjDpDpUB = !_needDpDp;

        cleanSums();

        final long jMax = max(2, (long) Math.ceil(w * pow(y, 2 - _p) / ((2 - _p) * _phi)));
        long j;
        int cnt = 0;
        // Start at the maximum and spread out until the change is negligible
        while (!(WjLB && WjUB && WjDpLB && WjDpUB && WjDpDpLB && WjDpDpUB) && cnt < _max_iter_cnt) {
            j = jMax + cnt;
            if (!WjUB) WjUB = Wj(log_y, log_w, j);
            if (!WjDpUB) WjDpUB = WjDp(logs_sumWdp, p1alphaw, ps_sumWdp, pphiy_sumWdp, j);
            if (!WjDpDpUB) WjDpDpUB = WjDpDp(y, w, negwy, denom_W_dp_dp_exped, log_p1_phi_wy, j);

            j = jMax - cnt - 1;
            if (j < 1) {
                WjLB = true;
                WjDpLB = true;
                WjDpDpLB = true;
            } else {
                if (!WjLB) WjLB = Wj(log_y, log_w, j);
                if (!WjDpLB) WjDpLB = WjDp(logs_sumWdp, p1alphaw, ps_sumWdp, pphiy_sumWdp, j);
                if (!WjDpDpLB) WjDpDpLB = WjDpDp(y, w, negwy, denom_W_dp_dp_exped, log_p1_phi_wy, j);
            }
            cnt++;
        }
    }

    private boolean Wj(double log_y, double log_w, long j) {
        final double expInner = ((1 - _alpha) * j) * log_w +
                (_alpha * j) * (_logp1 - log_y) -
                j * (1 - _alpha) * _log_phi - j * _logp2 - logGamma(j + 1) - logGamma(-j * _alpha);
        if (_logWMax == 0) {
            _logWMax = expInner;
        }
        double wSumInc;
        wSumInc = exp(expInner - _logWMax);

        _wSum += wSumInc;
        if (_needDp || _needDpDp)  // can use more precision in grad and hess
            return (abs(wSumInc) + _epsilon) / (abs(_wSum) + _epsilon) < _epsilon && expInner - _logWMax < -37;
        return expInner - _logWMax < -37;
    }

    private boolean WjDp(double logs_sumWdp, double p1alphaw, double ps_sumWdp, double pphiy_sumWdp, long j) {
        double wDpSumInc;
        final double log2_inner = _p2 * digamma(-j * _alpha) + logs_sumWdp;
        wDpSumInc = Math.signum(log2_inner) * Math.signum(ps_sumWdp) * exp(
                log(j) +
                        log(Math.abs(log2_inner)) +
                        j * log(p1alphaw) -
                        log(abs(ps_sumWdp)) -
                        j * log(pphiy_sumWdp) -
                        logGamma(j + 1) -
                        logGamma(-j * _alpha) - _logWMax);
        _wDpSum += wDpSumInc;
        return (Math.abs(wDpSumInc) + _epsilon) / (abs(_wDpSum) + _epsilon) < _epsilon;
    }

    private boolean WjDpDp(double y, double w, double negwy, double denom_W_dp_dp_exped, double log_p1_phi_wy, long j) {
        if (j <= 1) return false; // Undefined
        double wDpDpSumInc;
        final double mja = -(_alpha * j);
        final double psi_mja = digamma(mja);
        final double p1jp2p = pow(_p1, (j * _p + 2 * _p) * _invp1);
        final double p1jp2 = pow(_p1, (j * _p + 2) * _invp1);
        final double jlogp1 = j * log(_p - 1);
        final double logdpdp1 = ((_p * (psi_mja - 2) + _p * log_p1_phi_wy - 2 * psi_mja + 3) * j *
                p1jp2p * _p2 * log(y) - _p1sq * _p2 * (_p1 + _p2 - _p2 * psi_mja) * j *
                p1jp2 * log(w) - (_p2sq * j * pow(psi_mja, 2) - 2 * (jlogp1 - 2 * j + 11) * pow(_p, 2) + 5 * pow(_p, 3) -
                ((_p1 + _p2) * _p2 - _p2sq * psi_mja) * j * log(_phi) - _p2sq * j * trigamma(mja) +
                (7 * jlogp1 - 12 * j + 32) * _p - 6 * jlogp1 + ((jlogp1 - 4 * j + 10) * pow(_p, 2) - 2 * pow(_p, 3) -
                2 * (2 * jlogp1 - 7 * j + 8) * _p + 4 * jlogp1 - 12 * j + 8) * psi_mja + 9 * j - 15) *
                p1jp2p + (_p1sq * _p2 * j * p1jp2 * _p * log(w) - (j * _p2 * _p * log(_phi) +
                j * _p2 * _p * psi_mja + (jlogp1 - 2 * j + 4) * pow(_p, 2) - 2 * pow(_p, 3) - j * _p * (2 * log(_p - 1) - 3) - 2) * p1jp2p) *
                log_p1_phi_wy + 2 * (_p1sq * _p2 * j * p1jp2 * log(w) + j * p1jp2p * _p2 * log(y) -
                (j * _p2 * log(_phi) + j * _p2 * psi_mja + (jlogp1 - 2 * j + 8) * _p - 3 * pow(_p, 2) - 2 * jlogp1 +
                        3 * j - 5) * p1jp2p) * (-log_p1_phi_wy));


        wDpDpSumInc = -Math.signum(logdpdp1) * pow(Math.signum(negwy) * Math.signum(denom_W_dp_dp_exped), j) *
                exp(log(abs(logdpdp1)) + j * log(abs(negwy)) - _logDenom_p - log(j - 1) - j * log(abs(denom_W_dp_dp_exped)) - logGamma(j - 1)
                        - logGamma(mja) - _logWMax);
        _wDpDpSum += wDpDpSumInc;
        return (abs(wDpDpSumInc) + _epsilon) / (abs(_wDpDpSum) + _epsilon) < _epsilon;
    }

    void calculateVkSums(double y, double w) {
        assert y > 0;
        final double logs = _logp1 + log(_phi) - log(w) - log(y);
        final double logssq = -pow(_logp1, 2) - pow(log(_phi), 2) - pow(log(w), 2) - pow(log(y), 2);
        final double log_w = log(w);
        final double log_y = log(y);
        final double log_pwy = _logp2 + (_alpha - 1) * log_w + _alpha * log_y;
        final double vkR = (_alpha - 1) * _log_phi + _alpha * _logp1 - (_alpha - 1) * log_w - _logp2 - _alpha * log_y; // r in R's tweedie::dtweedie.series.bigp

        // Indicators whether lower and upper bounds were reached.
        boolean VkLB = false;
        boolean VkUB = false;
        boolean VkDpLB = !_needDp;
        boolean VkDpUB = !_needDp;
        boolean VkDpDpLB = !_needDpDp;
        boolean VkDpDpUB = !_needDpDp;

        cleanSums();

        final long kMax = max(1, (long) (w * pow(y, 2 - _p) / ((_p - 2) * _phi)));
        long k;
        long cnt = 0;
        double mPiAlphaK, logGammaK1, logGammaKalpha1, digammaKalpha1;

        // Start at the maximum and spread out until the change is negligible
        while (!(VkLB && VkUB && VkDpLB && VkDpUB && VkDpDpLB && VkDpDpUB) && cnt < _max_iter_cnt) {
            k = kMax + cnt;
            mPiAlphaK = _pialpha * k;
            if (k > Integer.MAX_VALUE) { // prevents StackOverflowError from digamma
                _logVSum = Double.NEGATIVE_INFINITY;
                _logVMax = 0;
                _logVDpSum = Double.NaN;
                _logVDpDpSum = Double.NaN;
                break;
            }
            logGammaK1 = logGamma(k + 1);
            logGammaKalpha1 = logGamma(k * _alpha + 1);
            digammaKalpha1 = digamma(k * _alpha + 1);

            if (!VkUB) VkUB = Vk(vkR, k, mPiAlphaK, logGammaKalpha1, logGammaK1);
            if (!VkDpUB)
                VkDpUB = VkDp(log_y, log_w, k, log_pwy, mPiAlphaK, logGammaKalpha1, logGammaK1, digammaKalpha1);
            if (!VkDpDpUB)
                VkDpDpUB = VkDpDp(k, log_y, log_w, logs, logssq, mPiAlphaK, logGammaKalpha1, digammaKalpha1);

            k = kMax - cnt - 1;
            if (k < 1) {
                VkLB = true;
                VkDpLB = true;
                VkDpDpLB = true;
            } else {
                mPiAlphaK = _pialpha * k;
                logGammaK1 = logGamma(k + 1);
                logGammaKalpha1 = logGamma(k * _alpha + 1);
                digammaKalpha1 = digamma(k * _alpha + 1);

                if (!VkLB) VkLB = Vk(vkR, k, mPiAlphaK, logGammaKalpha1, logGammaK1);
                if (!VkDpLB)
                    VkDpLB = VkDp(log_y, log_w, k, log_pwy, mPiAlphaK, logGammaKalpha1, logGammaK1, digammaKalpha1);
                if (!VkDpDpLB)
                    VkDpDpLB = VkDpDp(k, log_y, log_w, logs, logssq, mPiAlphaK, logGammaKalpha1, digammaKalpha1);
            }
            cnt++;
        }
        _vDpSumSgn = Math.signum(_logVDpSum);
        _vDpDpSumSgn = Math.signum(_logVDpDpSum);

        // not adding _logVMax here since it gets eliminated in both grad and hess; in llh
        _logVSum = log(max(0, _logVSum));
        _logVDpSum = log(abs(_logVDpSum));
        _logVDpDpSum = log(abs(_logVDpDpSum));
    }

    boolean Vk(double r, long k, double mPiAlphaK, double logGammaKalpha1, double logGammaK1) {
        double expInner = logGammaKalpha1 - logGammaK1 + k * r;
        if (_logVMax == 0) {
            _logVMax = expInner;
        }
        double vSumInc = exp(expInner - _logVMax) * sin(mPiAlphaK);

        if (k % 2 == 1)
            vSumInc *= -1;

        _logVSum += vSumInc; // logVSum wasn't transformed by log at this point
        if (_needDp || _needDpDp) // can use more precision for grad and hess
            return (abs(vSumInc) + _epsilon) / (abs(_logVSum) + _epsilon) < _epsilon && expInner - _logVMax < -37;
        return expInner - _logVMax < -37;
    }

    boolean VkDp(double log_y, double log_w, long k, double log_pwy, double mPiAlphaK, double logGammaKalpha1,
                 double logGammaK1, double digammaKalpha1) {
        final double logInvDenom = -k * log_pwy - logGammaK1;

        final double logInner = sin(mPiAlphaK) * (_invp1sq * (_log_phi - log_w - log_y + digammaKalpha1) -
                _invp2 + _invp1sq * (_logp1 + _p2)) - PI * _invp1sq * cos(mPiAlphaK);

        double vDpSumInc = Math.signum(logInner) * exp(k * _alpha * _logp1 + logInvDenom + logGammaKalpha1 + log(abs(logInner)) +
                log(k) + k * _logPhip1inv - _logVMax);

        if (k % 2 == 1)
            vDpSumInc *= -1;

        _logVDpSum += vDpSumInc;
        return (abs(vDpSumInc) + _epsilon) / (abs(_logVDpSum) + _epsilon) < _epsilon;
    }

    boolean VkDpDp(long k, double log_y, double log_w, double logs, double logssq, double mPiAlphaK, double logGammaKalpha1, double digammaKalpha1) {
        if (k < 1) return true;
        double vDpDpSumInc = -(2 * PI * (k * _p2 * digammaKalpha1 + (_p * (logs - 2) - 2 * logs + 3) * k - _p2sq - _p2) * _p2 * cos(mPiAlphaK) -
                (_ppp * (2 * logs - 5) - _p2sq * k * pow(digammaKalpha1, 2) + 4 * _pisq * k + (_pisq * k + k * logssq - 2 * (k * _log_phi - k * log_w - k * log_y - 2 * k + 5) * _logp1 +
                        2 * (k * log_w + k * log_y + 2 * k - 5) * _log_phi - 2 * (k * log_y + 2 * k - 5) * log_w -
                        2 * (2 * k - 5) * log_y - 4 * k + 22) * _pp +
                        4 * k * logssq - _p2sq * k * trigamma(k * _alpha + 1) - 2 * (2 * _pisq * k + 2 * k * logssq - (4 * k * _log_phi - 4 * k * log_w - 4 * k * log_y - 7 * k + 8) * _logp1 +
                        (4 * k * log_w + 4 * k * log_y + 7 * k - 8) * _log_phi - (4 * k * log_y + 7 * k - 8) * log_w - (7 * k - 8) * log_y - 6 * k + 16) * _p -
                        4 * (2 * k * _log_phi - 2 * k * log_w - 2 * k * log_y - 3 * k + 2) * _logp1 + 4 * (2 * k * log_w + 2 * k * log_y + 3 * k - 2) * _log_phi - 4 * (2 * k * log_y + 3 * k - 2) * log_w -
                        4 * (3 * k - 2) * log_y - 2 * ((k * logs - 2 * k + 5) * _pp - _ppp - (4 * k * logs - 7 * k + 8) * _p + 4 * k * logs - 6 * k + 4) * digammaKalpha1 - 9 * k + 15) * sin(-mPiAlphaK)) *
                pow(-1, k) * exp(k * _alpha * _logp1 + (k - k * _alpha) * log_w + (-k * _alpha) * log_y +
                logGammaKalpha1 + _logInvDenom2ConstPart - k * _logp2 - k * _invp1 * _log_phi - logGamma(k) - _logVMax);
        _logVDpDpSum += vDpDpSumInc;
        return (abs(vDpDpSumInc) + _epsilon) / (abs(_logVDpDpSum) + _epsilon) < _epsilon;
    }

    // Fourier inversion part ------------------------------------------------------------------------------------------
    private final static double[] absc = new double[]{
            0.003064962185159399599837515282, 0.009194771386432905660446301965,
            0.015324235084898182521206955187, 0.021453122959774875710969865850,
            0.027581204711919792005314633343, 0.033708250072480593073631638390,
            0.039834028811548446991075422829, 0.045958310746809061253514983036,
            0.052080865752192069539905361353, 0.058201463766518225784185602834,
            0.064319874802144239023249383536, 0.070435868953604666153900382142,
            0.076549216406251049948927800415, 0.082659687444887164353701791697,
            0.088767052462401033197103572547, 0.094871081968392542704826553290,
            0.100971546597796779654032661711, 0.107068217119502664957941817647,
            0.113160864444966535735659363127, 0.119249259636820398311485291742,
            0.125333173917474477443434466295, 0.131412378677713714836272629327,
            0.137486645485288105916765744041, 0.143555746093496028326086388915,
            0.149619452449761269896555404557, 0.155677536704201868733576930026,
            0.161729771218192097670396378817, 0.167775928572916122050173726166,
            0.173815781577913441857674570201, 0.179849103279615923911549657532,
            0.185875666969875702472236866925, 0.191895246194484031532212497950,
            0.197907614761680478165928320777, 0.203912546750652373672707540209,
            0.209909816520023939645511745766, 0.215899198716335033454427616562,
            0.221880468282509041300087915261, 0.227853400466309585770119383596,
            0.233817770828785853609588230029, 0.239773355252706182882960206371,
            0.245719929950979243393760498293, 0.251657271475063337717870126653,
            0.257585156723362629360707387605, 0.263503362949610298038294331491,
            0.269411667771238594326632664888, 0.275309849177735044278847453825,
            0.281197685538984665232220550024, 0.287074955613597970760508815147,
            0.292941438557224431704639755480, 0.298796913930850727147969791986,
            0.304641161709084229425315015760, 0.310473962288420446409276109989,
            0.316295096495494865163067288449, 0.322104345595318808381790631756,
            0.327901491299498415443736121233, 0.333686315774437136649765989205,
            0.339458601649521019005817379366, 0.345218132025286672526220854706,
            0.350964690481571417457473671675, 0.356698061085645612422467820579,
            0.362418028400326441840206825873, 0.368124377492073107109860075070,
            0.373816893939063366048003445030, 0.379495363839250532400626525487,
            0.385159573818401101963360133595, 0.390809311038112505709563038181,
            0.396444363203810545837058043617, 0.402064518572726958822727283405,
            0.407669565961855551172732248233, 0.413259294755887629513324554864,
            0.418833494915126280933037605791, 0.424391956983378670908990670796,
            0.429934472095826580861910315434, 0.435460831986874741250659326397,
            0.440970828997976571628214514931, 0.446464256085437494192547092098,
            0.451940906828194099986717446882, 0.457400575435571277171931114935,
            0.462843056755014803371750531369, 0.468268146279799957198974880157,
            0.473675640156716648565549121486, 0.479065335193728458751394327919,
            0.484437028867608643345477048570, 0.489790519331549878412346288314,
            0.495125605422748638062557802186, 0.500442086669964369960439398710,
            0.505739763301052192012718933256, 0.511018436250469942905283460277,
            0.516277907166757477064322756632, 0.521517978419990813065965085116,
            0.526738453109207749314180091460, 0.531939135069806612321485772554,
            0.537119828880917804525552128325, 0.542280339872746153240257172001,
            0.547420474133886614254151936620, 0.552540038518610221451865527342,
            0.557638840654121947792987157300, 0.562716688947789034358493154286,
            0.567773392594340675643138638407, 0.572808761583037395759276932949,
            0.577822606704811114752828871133, 0.582814739559374461741469986009,
            0.587784972562300778164967596240, 0.592733118952072146612408687361,
            0.597658992797097665672367838852, 0.602562409002699417293058559153,
            0.607443183318068347098517278937, 0.612301132343186949036351052200,
            0.617136073535721196847703140520, 0.621947825217879390891084767645,
            0.626736206583239252587702594610, 0.631501037703541601153744977637,
            0.636242139535451722842651633982, 0.640959333927286434295922390447,
            0.645652443625708949426211802347, 0.650321292282389107342055467598,
            0.654965704460630293581857586105, 0.659585505641960279099578201567,
            0.664180522232690528916521088831, 0.668750581570438429324099161022,
            0.673295511930615209195138959331, 0.677815142532878778247606987861,
            0.682309303547550927149245580949, 0.686777826101999111507723227987,
            0.691220542286981598500972268084, 0.695637285162956975348436117201,
            0.700027888766357242467108790152, 0.704392188115823825178551942372,
            0.708730019218407059078401744046, 0.713041219075728482934550811478,
            0.717325625690105272980190420640, 0.721583078070637928824737628020,
            0.725813416239259323603505436040, 0.730016481236746561656048015720,
            0.734192115128692979197921886225, 0.738340161011444173766449239338,
            0.742460463017992289280755358050, 0.746552866323834107831203255046,
            0.750617217152788063216917180398, 0.754653362782772507699746711296,
            0.758661151551544898907764036267, 0.762640432862400241553757496149,
            0.766591057189829894191746006982, 0.770512876085140518966909439769,
            0.774405742182031731069002944423, 0.778269509202133780156884768076,
            0.782104031960504153531132942589, 0.785909166371082990032448378770,
            0.789684769452107193643541904748, 0.793430699331483024749900323513,
            0.797146815252117502126338877133, 0.800832977577207061337105642451,
            0.804489047795484579772562483413, 0.808114888526424324233232709958,
            0.811710363525404265949703130900, 0.815275337688824874859960800677,
            0.818809677059186835634818635299, 0.822313248830123577626238784433,
            0.825785921351392504519139947661, 0.829227564133821259950707371900,
            0.832638047854211360565557242808, 0.836017244360197420149916069931,
            0.839365026675062742000932303199, 0.842681269002510502375002943154,
            0.845965846731390636037417607440, 0.849218636440382645957924978575,
            0.852439515902632671817684695270, 0.855628364090346482662141625042,
            0.858785061179337283476797892945, 0.861909488553529001819697441533,
            0.865001528809411501796944321541, 0.868061065760453942630192614160,
            0.871087984441469842522565159015, 0.874082171112937289514377425803,
            0.877043513265272300927222204336, 0.879971899623057107753254513227,
            0.882867220149221032521325014386, 0.885729366049175403929893946042,
            0.888558229774901842112910799187, 0.891353705028992693293332649773,
            0.894115686768646500404145172070, 0.896844071209613846740182907524,
            0.899538755830097902510544827237, 0.902199639374606787711741162639,
            0.904826621857757973366176429408, 0.907419604568035498282085882238,
            0.909978490071499224178808162833, 0.912503182215446018155091678636,
            0.914993586132022862500434712274, 0.917449608241791114693342024111,
            0.919871156257243582921034885658, 0.922258139186271863607657905959,
            0.924610467335585606285519588710, 0.926928052314082817630946919962,
            0.929210807036171093642451523920, 0.931458645725040335072719699383,
            0.933671483915885391802191861643, 0.935849238459080523533373252576,
            0.937991827523303123292919281084, 0.940099170598609368276754594262,
            0.942171188499458911458361853875, 0.944207803367690501339382080914,
            0.946208938675447974731014255667, 0.948174519228055068253979698056,
            0.950104471166841935136915253679, 0.951998721971919814599516485032,
            0.953857200464905963244177655724, 0.955679836811598848456128507678,
            0.957466562524601938477530893579, 0.959217310465897199378559889738,
            0.960932014849367743813957076782, 0.962610611243270297698870763270,
            0.964253036572656041514051139529, 0.965859229121740714418820061837,
            0.967429128536223759127210541919, 0.968962675825556618569578404276,
            0.970459813365158741049754098640, 0.971920484898583625366086380382,
            0.973344635539632463405723683536, 0.974732211774417045546670124168,
            0.976083161463370263533079196350, 0.977397433843205876158322098490,
            0.978674979528826316510503602331, 0.979915750515178207713518077071,
            0.981119700179057141475880143844, 0.982286783280859610023583172733,
            0.983416955966283978796127485111, 0.984510175767978390481971473491,
            0.985566401607137931861757351726, 0.986585593795049176080169672787,
            0.987567714034582877502543851733, 0.988512725421635041200829618901,
            0.989420592446515700935094628221, 0.990291280995286848920500233362,
            0.991124758351048074089817419008, 0.991920993195171463163717362477,
            0.992679955608486541684953863296, 0.993401617072414810927227790671,
            0.994085950470055879080177874130, 0.994732930087228184312664325262,
            0.995342531613465753004277303262, 0.995914732142977210394008125149,
            0.996449510175577368720212234621, 0.996946845617603827349739731289,
            0.997406719782849782163225427212, 0.997829115393562893210344100225,
            0.998214016581612795242506308568, 0.998561408890039747809908021736,
            0.998871279275449386325647083140, 0.999143616112378230020851788140,
            0.999378409202599238270181558619, 0.999575649798310816862567662611,
            0.999735330671042699002271092468, 0.999857446369979419031892575731,
            0.999941994606845629967040167685, 0.999988990984381875826159102871
    };
    private final static double[] weights = new double[]{
            0.006129905175405764294893629085, 0.006129674838036492517945319491,
            0.006129214171953068987508395082, 0.006128523194465529920493818139,
            0.006127601931538031489188345091, 0.006126450417787949499770494555,
            0.006125068696484561869830542946, 0.006123456819547496918221263229,
            0.006121614847544605726714639360, 0.006119542849689838838467270676,
            0.006117240903840640703359454733, 0.006114709096494903503571372028,
            0.006111947522787882815242799239, 0.006108956286488514790533610466,
            0.006105735499995448845034218266, 0.006102285284333060395856040969,
            0.006098605769146656953305640769, 0.006094697092697685079920599804,
            0.006090559401858643313876218173, 0.006086192852107506767733724473,
            0.006081597607521639116401335201, 0.006076773840772089693706980995,
            0.006071721733116766488158599913, 0.006066441474393663782493923975,
            0.006060933263013820911091489307, 0.006055197305953895908769979428,
            0.006049233818748141547350094527, 0.006043043025480822859341056841,
            0.006036625158776993613218841972, 0.006029980459794644954973907858,
            0.006023109178214984885113558732, 0.006016011572233289327049643447,
            0.006008687908549399311897154519, 0.006001138462357170390293337192,
            0.005993363517334814559445188564, 0.005985363365633674000154673678,
            0.005977138307867538649653660343, 0.005968688653101249068366751516,
            0.005960014718839098078750904364, 0.005951116831012847295523382485,
            0.005941995323969674266950669050, 0.005932650540459486442068648415,
            0.005923082831621807528565959444, 0.005913292556972981305063452595,
            0.005903280084392509806379134574, 0.005893045790109107881504790782,
            0.005882590058686672750132284904, 0.005871913283009922226995946914,
            0.005861015864269402374231443531, 0.005849898211946678167061364206,
            0.005838560743798747003363569519, 0.005827003885842343793022291010,
            0.005815228072338094258975083051, 0.005803233745774116596194414086,
            0.005791021356849213735928927349, 0.005778591364456383931702543322,
            0.005765944235664991271428370112, 0.005753080445703687324787711788,
            0.005740000477942362212824267687, 0.005726704823874017614981912772,
            0.005713193983096204360550007806, 0.005699468463292455683300019587,
            0.005685528780212967606133567244, 0.005671375457655504839782345528,
            0.005657009027445287531465911712, 0.005642430029415474758425208535,
            0.005627639011386637545031330632, 0.005612636529146218002106483169,
            0.005597423146427572132610706035, 0.005581999434888915492813943331,
            0.005566365974091757977404437696, 0.005550523351479155591270409076,
            0.005534472162353648236332581689, 0.005518213009854874839810179310,
            0.005501746504936822455833489443, 0.005485073266345072764971213530,
            0.005468193920593387644113470003, 0.005451109101940144682774125329,
            0.005433819452364725861859273692, 0.005416325621543047544315108155,
            0.005398628266823572718902113365, 0.005380728053202113274344764449,
            0.005362625653297344377468114374, 0.005344321747325165260222856745,
            0.005325817023073333225657854939, 0.005307112175875508715272577120,
            0.005288207908585203231854876549, 0.005269104931549262356427210108,
            0.005249803962581399939535398147, 0.005230305726934937789185386947,
            0.005210610957275768270746674204, 0.005190720393654706284192190680,
            0.005170634783479783128101736622, 0.005150354881487986119514843608,
            0.005129881449717141328470404460, 0.005109215257477111096773292331,
            0.005088357081320884003905469228, 0.005067307705015408093862649963,
            0.005046067919512328692199787383, 0.005024638522917936923894988155,
            0.005003020320463469512717313847, 0.004981214124474673925202505842,
            0.004959220754341320605562692947, 0.004937041036486572963271068915,
            0.004914675804335549846868502755, 0.004892125898284490834178050989,
            0.004869392165668924923882521227, 0.004846475460731644938072726347,
            0.004823376644591045349363955808, 0.004800096585208414069756432951,
            0.004776636157355448886185911306, 0.004752996242581400063165197878,
            0.004729177729179922379243450337, 0.004705181512155669557029291639,
            0.004681008493190663179162047669, 0.004656659580610528897937072657,
            0.004632135689350181870227451952, 0.004607437740919578979259529916,
            0.004582566663369058192201155322, 0.004557523391254390821014652602,
            0.004532308865601840722203696998, 0.004506924033872596394023624100,
            0.004481369849927314963355939881, 0.004455647273990272390353784004,
            0.004429757272613176269371315641, 0.004403700818638965619467029455,
            0.004377478891165111941907728266, 0.004351092475507059922912311833,
            0.004324542563160944756706083325, 0.004297830151766558401393858446,
            0.004270956245069621078080945864, 0.004243921852884336397282449838,
            0.004216727991055227442451780462, 0.004189375681419113366110718033,
            0.004161865951766540415446282708, 0.004134199835803465360173358789,
            0.004106378373112003904443767510, 0.004078402609111729873458962459,
            0.004050273595020140865452518142, 0.004021992387813410133046154726,
            0.003993560050186265031335608455, 0.003964977650512617468603338011,
            0.003936246262804875099827750518, 0.003907366966673973297796695903,
            0.003878340847288519813162999128, 0.003849168995334298088578650621,
            0.003819852506973011631308256852, 0.003790392483801296400619529336,
            0.003760790032809268722963080833, 0.003731046266338848560462082560,
            0.003701162302042068034252375597, 0.003671139262839005247551771305,
            0.003640978276875699460451984990, 0.003610680477481611767159863646,
            0.003580247003126992098170910950, 0.003549678997380481711154676105,
            0.003518977608865717348479718041, 0.003488143991218293615136358810,
            0.003457179303042459076605874557, 0.003426084707867667507319442421,
            0.003394861374104690254077665301, 0.003363510475001769365471782081,
            0.003332033188600574697552092474, 0.003300430697691895606804557417,
            0.003268704189771169145439788650, 0.003236854856993954046573414018,
            0.003204883896131119781075513586, 0.003172792508523682164511825476,
            0.003140581900037948612225413569, 0.003108253281020026750902651713,
            0.003075807866250348642650491726, 0.003043246874898155977795521920,
            0.003010571530475542479515782546, 0.002977783060791477226514345489,
            0.002944882697905871343779793392, 0.002911871678083000243575373389,
            0.002878751241745274112165953184, 0.002845522633426492749991743025,
            0.002812187101725080462522043945, 0.002778745899257360849748943465,
            0.002745200282610227044549633391, 0.002711551512294096445698787790,
            0.002677800852695407917564152100, 0.002643949572029331059747070398,
            0.002609998942291816281802141475, 0.002575950239212147514084039202,
            0.002541804742204615413792012646, 0.002507563734320777774911004343,
            0.002473228502201043829678006603, 0.002438800336026468017908142016,
            0.002404280529470125687963033556, 0.002369670379648584207510353394,
            0.002334971187073220984936616773, 0.002300184255601201484958684418,
            0.002265310892386644160689801453, 0.002230352407831378593050519754,
            0.002195310115535779524331694290, 0.002160185332249389255493410289,
            0.002124979377821471521886609324, 0.002089693575151347661178480308,
            0.002054329250138731913916112504, 0.002018887731633921007318166474,
            0.001983370351387804628867650436, 0.001947778444001947023567211659,
            0.001912113346878267219550173728, 0.001876376400168962114978210565,
            0.001840568946726033397465194241, 0.001804692332050859783151852689,
            0.001768747904243635031551473702, 0.001732737013952766495436530469,
            0.001696661014324110389878130789, 0.001660521260950091485333879326,
            0.001624319111818701803773290493, 0.001588055927262728948823333752,
            0.001551733069908424120231238419, 0.001515351904624347286962282588,
            0.001478913798470224069681044909, 0.001442420120645365689757144700,
            0.001405872242437510889756513421, 0.001369271537171097780430373270,
            0.001332619380155811508736896087, 0.001295917148634917904354013629,
            0.001259166221733550722339245453, 0.001222367980406950313532199459,
            0.001185523807388666516285380403, 0.001148635087138642285956025013,
            0.001111703205791432849669497784, 0.001074729551104118682389176875,
            0.001037715512404510350211173098, 0.001000662480539097265105907830,
            0.000963571847821184873997268916, 0.000926445007979156964772471383,
            0.000889283356104514192963517161, 0.000852088288600481814569209682,
            0.000814861203130772785048485662, 0.000777603498568675576864406285,
            0.000740316574946985831578993853, 0.000703001833408759078253291719,
            0.000665660676159933917435396200, 0.000628294506424452014331505367,
            0.000590904728403224407604077406, 0.000553492747240409460780796724,
            0.000516059969000759975743530816, 0.000478607800667961002377692736,
            0.000441137650179552207388433693, 0.000403650926533314074063502064,
            0.000366149040035628262328842863, 0.000328633402852310262977353350,
            0.000291105430251488786486113725, 0.000253566543570602365847976856,
            0.000216018177976967721805670597, 0.000178461805545972196102716412,
            0.000140899017388190165439576518, 0.000103331903496931828490348892,
            0.000065765731659236768705603660, 0.000028252637373961186168999649
    };


    private double tweedieInversion(double y, double mu, double w) {
        assert _p != 1 && _p != 2;

        if (_p < 2 && _p > 1) {
            if (y < 0)
                return Double.NEGATIVE_INFINITY; // skip; should be -Inf if we wouldn't skip that point
            else if (y == 0) {
                return pow(mu, 2 - _p) / (_phi * _p2);
            }
        } else if (_p > 2) {
            if (y <= 0)
                return Double.NEGATIVE_INFINITY; // skip; should be -Inf if we wouldn't skip that point
        }
        double dev = deviance(y, mu, _p);
        // method 3 in the paper - transform phi and estimate density on the mu=1, y=1 where it should be highest and
        // then transform the result so it corresponds to the value of the untransformed pdf 
        double phi = _phi / pow(y, -_p2);
        if (_p > 1 && _p < 2)
            return w * (log(max(0, smallP(1, 1, phi))) - log(y) - dev / (2 * _phi));
        else if (_p > 2)
            return w * (log(max(0, bigP(1, 1, phi))) - log(y) - dev / (2 * _phi));

        return 0;
    }

    private final static class ZeroBounds {
        final double lowerBound;
        final double upperBound;
        final double funcLo;
        final double funcHi;

        private ZeroBounds(double lowerBound, double upperBound, double funcLo, double funcHi) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.funcLo = funcLo;
            this.funcHi = funcHi;
        }
    }

    private final static class FindKMaxResult {
        int _mMax;
        double _kMax, _tMax;

        FindKMaxResult(double kMax, double tMax, int mMax) {
            _kMax = kMax;
            _tMax = tMax;
            _mMax = mMax;
        }
    }


    double calcCGFRe(double x, double phi) { // calccgf in fortran
        double psi = atan((1. - _p) * x * phi);
        double front = 1 / (phi * (2 - _p));
        double denom = pow(cos(psi), _alpha);
        return front * cos(psi * _alpha) / denom - front;
    }

    double calcCGFIm(double y, double x, double phi) { // calccgf and imgcgf in fortran
        double psi = atan((1. - _p) * x * phi);
        double front = 1 / (phi * (2 - _p));
        double denom = pow(cos(psi), _alpha);
        return front * sin(psi * _alpha) / denom - x * y;
    }

    double calcDCGFRe(double x, double phi) {
        double psi = atan((1. - _p) * x * phi);
        double alpha = 1. / (1. - _p);
        double denom = pow(cos(psi), alpha);

        return -(sin(psi * alpha) / denom);
    }

    double calcDCGFIm(double y, double x, double phi) {
        double psi = atan((1. - _p) * x * phi);
        double alpha = 1. / (1. - _p);
        double denom = pow(cos(psi), alpha);

        return cos(psi * alpha) / denom - y;
    }


    double imgdcgf(double x, double phi) {
        double psi = atan((1 - _p) * x * phi);
        double alpha = 1 / (1 - _p);
        return cos(psi * alpha) / exp(alpha * log(cos(psi)));
    }

    private final class ImgDDCGF implements Function2<Double, Double, Double> {
        private final double _transformedPhi;

        public ImgDDCGF(double phi) {
            _transformedPhi = phi;
        }

        @Override
        public Double apply(Double x, Double unusedHere) {
            final double psi = atan((1.0 - _p) * x * _transformedPhi);
            final double alpha = _p / (1.0 - _p);
            final double top = sin(psi * alpha);
            final double bottom = exp(alpha * log(abs(cos(psi))));
            return -_transformedPhi * top / bottom;
        }
    }

    private final class DK implements Function2<Double, Double, Double> {
        private final double _transformedPhi;

        public DK(double phi) {
            _transformedPhi = phi;
        }

        @Override
        public Double apply(Double y, Double x) {
            return calcDCGFIm(y, x, _transformedPhi);
        }
    }

    private FindKMaxResult findKMax(double y, double phi) {
        final double largest = 1e30;
        final int largeInt = 100000000;
        double psi = (PI / 2) * (1 - _p) / (2 * _p - 1);
        double z2 = 1 / (phi * (1 - _p)) * tan(psi);
        double dz2 = imgdcgf(z2, phi) - y;
        double z1, dz1, z, dz, zLo, zHi, fLo, fHi, tMax, kMax;
        int mMax;
        DK dk = new DK(phi);
        if (_p > 2) {
            double front = -1 / (phi * (1 - _p));
            double inner = (1 / y) * cos(-PI / (2. * (1. - _p)));
            z1 = (front * pow(inner, _p - 1.));
            dz1 = imgdcgf(z1, phi) - y;
            if (dz1 > 0) {
                if (z1 > z2) {
                    z = z1;
                    dz = dz1;
                } else {
                    z = z2;
                    dz = dz2;
                }
            } else {
                if (dz2 < 0) {
                    if (z1 > z2) {
                        z = z2;
                        dz = dz2;
                    } else {
                        z = z1;
                        dz = dz1;
                    }
                } else {
                    z = z2;
                    dz = dz2;
                }
            }
        } else { // 1 < p <2
            z = z2;
            dz = dz2;
        }

        if (dz > 0) {
            zLo = z;
            zHi = z + 10;
            fLo = dk.apply(y, zLo);
            fHi = dk.apply(y, zHi);

            while (fHi > 0 && zHi <= largest / 10.) {
                zLo = zHi;
                zHi = 1.1 * zHi + 1;
                fLo = fHi;
                fHi = dk.apply(y, zHi);
            }
        } else {
            zLo = z / 2;
            zHi = z;
            fLo = dk.apply(y, zLo);
            fHi = dk.apply(y, zHi);

            while (fLo < 0) {
                zHi = zLo;
                zLo = zLo / 2.0;

                fHi = fLo;
                fLo = dk.apply(y, zLo);
            }
        }
        if (zLo == zHi)
            z = zLo;
        else
            z = zLo - fLo * (zHi - zLo) / (fHi - fLo);

        z = newtonMethodWithBisection(y, zLo, zHi, z, dk, new ImgDDCGF(phi));
        tMax = z;
        kMax = calcCGFIm(y, tMax, phi); // in fortran imgcgf( y, tMax);

        if (kMax < 0) {
            kMax = abs(kMax);
            mMax = largeInt;
        } else {
            int dpmMax = (int) ((kMax / PI) - 0.5);
            mMax = Math.min(dpmMax, largeInt);
        }
        return new FindKMaxResult(kMax, tMax, mMax);

    }

    private final class IntegrateImCGF implements Function3<Double, Double, Integer, Double> { // intim in fortran
        private final double _transformedPhi;

        public IntegrateImCGF(double phi) {
            _transformedPhi = phi;
        }

        @Override
        public Double apply(Double y, Double x, Integer m) {
            double im = calcCGFIm(y, x, _transformedPhi);
            return -PI / 2. - m * PI + im;
        }
    }

    private double otherZero(double y, double phi) { // othzero in fortran
        double psi = (PI / 2) * (1.0 - _p) / (2 * _p - 1);
        double inflec = atan(psi) / ((1 - _p) * phi);
        double smallest = 1e-30;
        int m;
        double tLo, tHi, fLo, fHi, zStep, t0, kMax, tMax;
        IntegrateImCGF intIm = new IntegrateImCGF(phi);
        DK dk = new DK(phi);
        if (y >= 1) {
            m = -1;
            tLo = min(1e-5, inflec);
            tHi = max(inflec, 1e-5);
        } else {
            FindKMaxResult fndKMaxRes = findKMax(y, phi);
            kMax = fndKMaxRes._kMax;
            tMax = fndKMaxRes._tMax;
            if (kMax >= PI / 2.) {
                m = 0;
                tLo = smallest;
                tHi = tMax;
            } else {
                m = -1;
                tLo = min(tMax, inflec);
                tHi = max(tMax, inflec);
            }
        }
        fLo = intIm.apply(y, tLo, m);
        fHi = intIm.apply(y, tHi, m);
        zStep = abs(tHi - tLo);

        while ((fLo * fHi) > 0) {
            tLo = tHi;
            tHi = tHi + 0.2 * zStep;

            fLo = intIm.apply(y, tLo, m);
            fHi = intIm.apply(y, tHi, m);
        }
        t0 = tLo - fLo * (tHi - tLo) / (fHi - fLo);
        return newtonMethodWithBisectionWithM(y, tLo, tHi, t0, intIm, dk, m);
    }

    private ZeroBounds findBounds(double y, double phi) { // findsp in fortran
        ZeroFunction zeroFunction = new ZeroFunction(phi);
        double t = PI / y;
        double f1 = zeroFunction.apply(y, 0.01);
        double f2;
        double t3 = otherZero(y, phi);
        double tOld = t, tStep;
        t = min(t, t3);
        f2 = zeroFunction.apply(y, t);
        tStep = 0.2 * t;
        while ((f1 * f2) > 0 && f1 != f2) {
            tOld = t;
            t = tOld + tStep;
            f1 = f2;
            f2 = zeroFunction.apply(y, t);
        }
        return new ZeroBounds(tOld, t, f1, f2);
    }

    private double newtonMethodWithBisection(double y, double x1, double x2, double x0, Function2<Double, Double, Double> fun, Function2<Double, Double, Double> dfun) { // in fortran sfzro
        double maxit = 100, xl, xh, result, dx, dxOld, f, df;
        double fl = fun.apply(y, x1);
        double fh = fun.apply(y, x2);

        if (fl == 0)
            return x1;
        else if (fh == 0)
            return x2;
        else if (fl < 0) {
            xl = x1;
            xh = x2;
        } else {
            xl = x2;
            xh = x1;
        }
        result = x0;
        dxOld = abs(x2 - x1);
        dx = dxOld;

        f = fun.apply(y, result);
        df = dfun.apply(y, result);

        for (int i = 0; i < maxit; i++) {
            if (((result - xh * df - f) * (result - xl) * df - f) > 0 ||
                    abs(2 * f) > abs(dxOld * df)) {  // use bisection
                dxOld = dx;
                dx = 0.5 * (xh - xl);
                result = xl + dx;
                if (xl == result)
                    return result;
            } else {
                //Newton 's method
                dxOld = dx;
                if (df == 0)
                    return result;
                dx = f / df;
                if (result == result - dx)
                    return result;
            }

            f = fun.apply(y, result);
            df = dfun.apply(y, result);

            if (f < 0)
                xl = result;
            else
                xh = result;
        }
        return result;
    }

    private double newtonMethodWithBisectionWithM(double y, double x1, double x2, double x0, Function3<Double, Double, Integer, Double> fun, Function2<Double, Double, Double> dfun, int m) { // in fortran sfzro2
        int maxit = 100;
        double xl, xh, result, dxOld, dx, f, df;
        double fl = fun.apply(y, x1, m);
        double fh = fun.apply(y, x2, m);

        if (fl == 0) {
            return x1;
        } else if (fh == 0) {
            return x2;
        } else if (fl < 0) {
            xl = x1;
            xh = x2;
        } else {
            xl = x2;
            xh = x1;
        }

        if (x0 > xl && x0 < xh)
            result = x0;
        else
            result = (xl + xh) / 2;

        dxOld = abs(x2 - x1);
        dx = dxOld;


        f = fun.apply(y, result, m);
        df = dfun.apply(y, result);

        for (int i = 0; i < maxit; i++) {
            if (((result - xh * df - f) * (result - xl) * df - f) > 0 || abs(2 * f) > abs(dxOld * df)) { // use bisection
                dxOld = dx;
                dx = 0.5 * (xh - xl);
                result = xl + dx;
                if (xl == result)
                    return result;
            } else {
                // Newton 's method
                dxOld = dx;
                dx = f / df;
                if (result == result - dx)
                    return result;
            }

            if (abs(dx) < 1e-11)
                return result;

            f = fun.apply(y, result, m);
            df = dfun.apply(y, result);

            if (f < 0)
                xl = result;
            else
                xh = result;
        }
        return result;
    }


    private double gaussQuad(Function3<Double, Double, Double, Double> fun, double a, double b, double y, double mu) {
        double sum = 0, xLower, xUpper;
        for (int i = 0; i < weights.length; i++) {
            xLower = (b - a) / 2. * absc[i] + (b + a) / 2.;
            xUpper = (a - b) / 2. * absc[i] + (b + a) / 2.;
            sum += weights[i] * (fun.apply(y, mu, xLower) + fun.apply(y, mu, xUpper));
        }
        return sum * (b - a) / 2.;
    }

    private final class FunctionForVariancePowerBetween1And2 implements Function3<Double, Double, Double, Double> { // f2 in fortran
        private final double _transformedPhi;

        public FunctionForVariancePowerBetween1And2(double phi) {
            _transformedPhi = phi;
        }

        @Override
        public Double apply(Double y, Double mu, Double x) {
            final double lambda = pow(mu, 2 - _p) / (_transformedPhi * (2 - _p));
            if (x == 0) {
                return 1.;
            } else {
                final double rl = calcCGFRe(x, _transformedPhi);
                final double im = calcCGFIm(y, x, _transformedPhi);
                return (exp(rl) * cos(im) - exp(-lambda) * cos(x * y));
            }
        }
    }

    private final class FunctionForVariancePowerGreaterThan2 implements Function3<Double, Double, Double, Double> {

        private final double _transformedPhi;

        public FunctionForVariancePowerGreaterThan2(double phi) {
            _transformedPhi = phi;
        }

        @Override
        public Double apply(Double y, Double mu, Double x) {
            final double rl = calcCGFRe(x, _transformedPhi);
            final double im = calcCGFIm(y, x, _transformedPhi);
            return exp(rl) * cos(im);
        }
    }

    private final class ZeroFunction implements Function2<Double, Double, Double> {
        private final double _transformedPhi;

        public ZeroFunction(double phi) {
            _transformedPhi = phi;
        }

        @Override
        public Double apply(Double y, Double x) {
            final double rl = calcCGFRe(x, _transformedPhi);
            final double im = calcCGFIm(y, x, _transformedPhi);
            final double lambda = 1 / (_transformedPhi * (2 - _p)); // lambda with mu == 1
            return exp(rl) * cos(im) - exp(-lambda) * cos(x * y);
        }
    }

    private final class ZeroDerivFunction implements Function2<Double, Double, Double> {
        private final double _transformedPhi;

        public ZeroDerivFunction(double phi) {
            _transformedPhi = phi;
        }

        @Override
        public Double apply(Double y, Double x) {
            final double rl = calcCGFRe(x, _transformedPhi);
            final double im = calcCGFIm(y, x, _transformedPhi);
            final double drl = calcDCGFRe(x, _transformedPhi);
            final double dim = calcDCGFIm(y, x, _transformedPhi);
            final double lambda = 1 / (_transformedPhi * (2 - _p)); //lambda with mu == 1

            return (exp(rl) * (-dim * sin(im)) +
                    exp(rl) * drl * cos(im) +
                    exp(-lambda) * y * sin(x * y));
        }
    }

    private static class SidiAcceleration {
        final double[][] _mMatrix, _nMatrix; // references
        final double[] _wOld, _xVec; // references
        double _w;
        double _relErr, _absErr;

        private SidiAcceleration(double[][] mMatrix, double[][] nMatrix, double[] wOld, double[] xVec) {
            this._mMatrix = mMatrix;
            this._nMatrix = nMatrix;
            this._wOld = wOld;
            this._xVec = xVec;
        }

        void apply(double FF, double psi, double w, int znum) {
            final double largest = 1e30;
            _w = w;

            if (abs(psi) < 1e-31) {
                _w = FF;
                _relErr = 0;
                return;
            }

            _mMatrix[1][0] = FF / psi;
            _nMatrix[1][0] = 1 / psi;

            for (int i = 1; i < znum; i++) {
                double denom = 1. / _xVec[znum - i] - 1. / _xVec[znum];
                _mMatrix[1][i] = (_mMatrix[0][i - 1] - _mMatrix[1][i - 1]) / denom;
                _nMatrix[1][i] = (_nMatrix[0][i - 1] - _nMatrix[1][i - 1]) / denom;

            }
            if (!(abs(_mMatrix[1][znum - 1]) > largest || abs(_nMatrix[1][znum - 1]) > largest)) {
                if (znum > 1)
                    _w = _mMatrix[1][znum - 1] / _nMatrix[1][znum - 1];
                _wOld[0] = _wOld[1];
                _wOld[1] = _wOld[2];
                _wOld[2] = _w;
            }

            if (znum > 2) {
                _relErr = abs(_w - _wOld[0]) + abs(_w - _wOld[1]) / _w;
                _absErr = abs(_wOld[2] - _wOld[1]);
            } else
                _relErr = 1.0;

            System.arraycopy(_mMatrix[1], 0, _mMatrix[0], 0, znum);
            System.arraycopy(_nMatrix[1], 0, _nMatrix[0], 0, znum);
        }
    }

    private double smallP(double y, double mu, double phi) {
        double[][] mMatrix = MemoryManager.malloc8d(2, 101);
        double[][] nMatrix = MemoryManager.malloc8d(2, 101);
        double area, area0 = 0, area1 = 0, result, w = 0, tStep, zero1, lower, upper, fLo, fHi;
        ZeroFunction zeroFunction = new ZeroFunction(phi);
        ZeroDerivFunction zeroDerivFunction = new ZeroDerivFunction(phi);
        ZeroBounds zb = findBounds(y, phi);
        upper = zb.upperBound;
        lower = zb.lowerBound;
        fHi = zb.funcHi;
        fLo = zb.funcLo;
        double t0 = (upper - fHi * (upper - lower) / (fHi - fLo));
        double zero2 = newtonMethodWithBisection(y, lower, upper, t0, zeroFunction, zeroDerivFunction);

        int iteration;
        double[] wOld = new double[3];
        int numZr = 20;
        double zDelta = zero2 / numZr;
        double z1Lo;
        double z1Hi = 0;
        FunctionForVariancePowerBetween1And2 f2 = new FunctionForVariancePowerBetween1And2(phi);
        for (int i = 0; i < numZr; i++) {
            z1Lo = z1Hi;
            z1Hi = z1Hi + zDelta;
            area0 += gaussQuad(f2, z1Lo, z1Hi, y, mu);
        }

        zero1 = zero2;
        tStep = zero2 / 2;
        for (int i = 0; i < 4; i++) {
            lower = zero1 + tStep * 0.05;
            upper = zero1 + tStep * 0.3;

            fLo = zeroFunction.apply(y, lower);
            fHi = zeroFunction.apply(y, upper);

            while (fLo * fHi > 0 && lower != upper) {
                lower = upper;
                upper = upper + 0.5 * tStep;

                fLo = zeroFunction.apply(y, lower);
                fHi = zeroFunction.apply(y, upper);
            }
            zero2 = newtonMethodWithBisection(y, lower, upper, t0, zeroFunction, zeroDerivFunction);

            result = gaussQuad(f2, zero1, zero2, y, mu);

            area1 = area1 + result;
            tStep = zero2 - zero1;
            zero1 = zero2;
            t0 = zero2 + (0.8 * tStep);
        }

        iteration = 0;
        area = 0;
        double[] xVec = MemoryManager.malloc8d(101); //new double[101]; //500
        xVec[0] = zero2;
        double relErr = Double.POSITIVE_INFINITY;
        SidiAcceleration sidi = new SidiAcceleration(mMatrix, nMatrix, wOld, xVec);
        while (iteration < 3 || (iteration < 100 && abs(relErr) > 1e-10)) {
            iteration++;

            lower = zero1 + 0.05 * tStep;
            upper = zero1 + 0.8 * tStep;

            fLo = zeroFunction.apply(y, lower);
            fHi = zeroFunction.apply(y, upper);

            while (fLo * fHi > 0 && lower != upper) {
                lower = upper;
                upper = upper + 0.5 * tStep;

                fLo = zeroFunction.apply(y, lower);
                fHi = zeroFunction.apply(y, upper);
            }
            t0 = lower - fLo * (upper - lower) / (fHi - fLo);
            zero2 = newtonMethodWithBisection(y, lower, upper, t0, zeroFunction, zeroDerivFunction);

            result = gaussQuad(f2, zero1, zero2, y, mu);
            xVec[iteration] = zero2;
            sidi.apply(area, result, w, iteration);
            w = sidi._w;
            relErr = sidi._relErr;

            if (iteration >= 3) {
                relErr = (area0 + area1 + w) == 0
                        ? Double.POSITIVE_INFINITY
                        : (abs(w - wOld[0]) + abs(w - wOld[1])) / (area0 + area1 + w);
            }
            area += result;
            tStep = zero2 - zero1;
            zero1 = zero2;
        }

        result = (area0 + area1 + w) / PI;
        return result;
    }


    private double bigP(double y, double mu, double phi) {
        final int maxit = 100;
        final double aimRelErr = 1e-10;
        double[][] mMatrix = MemoryManager.malloc8d(2, maxit + 1); //new double[2][maxit + 1];
        double[][] nMatrix = MemoryManager.malloc8d(2, maxit + 1); // new double[2][maxit + 1];
        double area, area0, result, w = 0, fLo, fHi, zero, zLo, zHi,
                zero1, zero2;
        IntegrateImCGF intIm = new IntegrateImCGF(phi);
        DK dk = new DK(phi);
        FunctionForVariancePowerGreaterThan2 f = new FunctionForVariancePowerGreaterThan2(phi);
        double largest = 1.e30;
        double smallest = 1.e-30;
        int m = -1;
        area = 0.0;
        int iteration = 0;
        double relErr = 1.0;
        boolean allOk;

        double[] wOld = new double[3];

        if (y >= 1) {
            zero1 = 0;
            zero = PI / (2 * y);
            zLo = 0.9 * PI / (2.0 * y);

            if (y > 1.0) {
                zHi = PI / (2.0 * (y - 1.0));
                fHi = intIm.apply(y, zHi, m);
            } else {
                zHi = zero * 2.0;
                fHi = intIm.apply(y, zHi, m);
            }
            fLo = intIm.apply(y, zLo, m);

            allOk = true;

            while (allOk && (fHi * fLo) > 0 && zHi != zLo) {
                zLo = zHi;
                zHi = zHi * 1.5;

                fLo = intIm.apply(y, zLo, m);
                fHi = intIm.apply(y, zHi, m);

                if (zHi > largest / 10.0)
                    allOk = false;
            }

            if (zHi > largest / 10.0)
                allOk = false;

            if (!allOk) {
                result = 0.0;
                return result;
            }

            zero2 = newtonMethodWithBisectionWithM(y, zLo, zHi, zero, intIm, dk, m);
            double[] xVec = MemoryManager.malloc8d(maxit + 1); //new double[maxit + 1];
            xVec[0] = zero2;
            SidiAcceleration sidi = new SidiAcceleration(mMatrix, nMatrix, wOld, xVec);
            area0 = gaussQuad(f, zero1, zero2, y, mu);
            while (iteration < 4 || (iteration < maxit && abs(relErr) > aimRelErr)) {
                m = m - 1;
                zero1 = zero2;
                zero = zero2;
                zLo = zero2;
                zHi = zero2 * 1.5;

                if (zHi > largest / 10.0) {
                    allOk = false;
                    fLo = 0.0;
                    fHi = 0.0;
                } else {
                    fLo = intIm.apply(y, zLo, m);
                    fHi = intIm.apply(y, zHi, m);
                }

                while (allOk && (fHi * fLo) > 0 && zHi != zLo) {
                    zLo = zHi;
                    zHi = zHi * 1.5;

                    fLo = intIm.apply(y, zLo, m);
                    fHi = intIm.apply(y, zHi, m);

                    zero = zHi - fHi * (zHi - zLo) / (fHi - fLo);

                    if (zHi > largest / 10.0)
                        allOk = false;
                }

                if (zHi > largest / 10.0)
                    allOk = false;

                if (!allOk) {
                    result = 0.0;
                    return result;
                }

                zero2 = newtonMethodWithBisectionWithM(y, zLo, zHi, zero, intIm, dk, m);

                result = gaussQuad(f, zero1, zero2, y, mu);
                iteration += 1;
                xVec[iteration] = zero2;
                sidi.apply(area, result, w, iteration);
                w = sidi._w;
                //relErr = sidi._relErr;

                if ((area0 + w) == 0)
                    relErr = Double.POSITIVE_INFINITY;
                else
                    relErr = (abs(w - wOld[0]) + abs((w - wOld[1]))) / (area0 + w);

                area += result;
            }

            result = area0 + w;
        } else { // y < 1
            FindKMaxResult fndKmax = findKMax(y, phi);
            final double kMax = fndKmax._kMax;
            final double tMax = fndKmax._tMax;
            final double mMax = fndKmax._mMax;

            if (kMax < PI / 2) {
                zero1 = 0.0;
                zero = tMax + PI / (2.0 * y);

                zLo = tMax;
                zHi = zero * 2.0;

                if (zHi > largest / 10.0) {
                    allOk = false;
                    fLo = 0.0;
                    fHi = 0.0;
                } else {
                    allOk = true;
                    fLo = intIm.apply(y, zLo, m);
                    fHi = intIm.apply(y, zHi, m);
                }
                while (allOk && (fHi * fLo) > 0 && zHi != zLo) {
                    zLo = zHi;
                    zHi = zHi * 1.5;

                    fLo = intIm.apply(y, zLo, m);
                    fHi = intIm.apply(y, zHi, m);

                    if (zHi > largest / 10.0)
                        allOk = false;
                }
                if (zHi > largest / 10.0)
                    allOk = false;
                if (!allOk) {
                    result = 0.0;
                    return result;
                }

                zero2 = newtonMethodWithBisectionWithM(y, zLo, zHi, zero, intIm, dk, m);

                double[] xVec = MemoryManager.malloc8d(maxit + 1);
                xVec[0] = zero2;
                SidiAcceleration sidi = new SidiAcceleration(mMatrix, nMatrix, wOld, xVec);
                area0 = gaussQuad(f, zero1, zero2, y, mu);

                while (iteration < 4 || (iteration < maxit && abs(relErr) > aimRelErr)) {
                    m = m - 1;
                    double diff = zero2 - zero1;
                    zero1 = zero2;

                    zLo = zero2 - 0.01 * diff;
                    zHi = zero2 + 2.0 * diff;

                    if (zHi > largest / 10.) {
                        allOk = false;
                        fLo = 0.0;
                        fHi = 0.0;
                    } else {
                        fLo = intIm.apply(y, zLo, m);
                        fHi = intIm.apply(y, zHi, m);
                    }

                    while (allOk && (fHi * fLo) > 0 && zHi != zLo) {
                        zLo = zHi;
                        zHi = zHi * 1.5;
                        fLo = intIm.apply(y, zLo, m);
                        fHi = intIm.apply(y, zHi, m);

                        if (zHi > largest / 10.0)
                            allOk = false;
                    }
                    if (zHi > largest / 10.0)
                        allOk = false;

                    if (!allOk) {
                        result = 0.0;
                        return result;
                    }

                    zero = zLo - fLo * (zHi - zLo) / (fHi - fLo);
                    zero2 = newtonMethodWithBisectionWithM(y, zLo, zHi, zero, intIm, dk, m);

                    result = gaussQuad(f, zero1, zero2, y, mu);

                    iteration += 1;
                    xVec[iteration] = zero2;
                    sidi.apply(area, result, w, iteration);
                    w = sidi._w;

                    if (area0 + w == 0)
                        relErr = Double.POSITIVE_INFINITY;
                    else
                        relErr = (abs(w - wOld[0]) + abs((w - wOld[1]))) / (area0 + w);

                    area += result;
                }
                result = area0 + w;
            } else { // kMax > PI/2
                zero1 = 0;
                zero = PI / (2. * (1 - y));

                m = 0;
                int firstM = 1;
                zLo = smallest;
                zHi = tMax;

                if (zHi > largest / 10.) {
                    allOk = false;
                    fLo = 0;
                    fHi = 0;
                } else {
                    allOk = true;
                    fLo = intIm.apply(y, zLo, m);
                    fHi = intIm.apply(y, zHi, m);
                }

                double diff = zHi - zLo;

                while (allOk && (fHi * fLo) > 0 && zHi != zLo) {
                    zLo = zHi;
                    zHi = zHi + 0.1 * diff;

                    fLo = intIm.apply(y, zLo, m);
                    fHi = intIm.apply(y, zHi, m);

                    if (zHi > largest / 10.0)
                        allOk = false;
                }
                if (zHi > largest / 10.0)
                    allOk = false;

                if (!allOk) {
                    result = 0.0;
                    return result;
                }

                zero2 = newtonMethodWithBisectionWithM(y, zLo, zHi, zero, intIm, dk, m);

                double[] xVec = MemoryManager.malloc8d(maxit + 1);
                xVec[0] = zero2;

                area0 = gaussQuad(f, zero1, zero2, y, mu);
                diff = zero2 - zero1;

                while (iteration < 4 || (iteration < maxit && abs(relErr) > aimRelErr)) {
                    zLo = zero2 - 1e-05 * diff;
                    zHi = zero2 + 2.0 * diff;

                    zero1 = zero2;
//                    m, firstm, zlo, zhi, zero = nextm(tMax, mMax, zero2, m, firstM, zLo); // expanded below
                    if (m < mMax) {
                        if (firstM == 1) {
                            m = m + 1;
                            zHi = tMax;
                        } else {
                            m = m - 1;
                            zLo = max(zLo, tMax);
                        }
                    } else if (m == mMax) {
                        if (firstM == 1) {
                            firstM++;
                            zero = tMax + (tMax - zero2);
                            zLo = tMax;
                        } else
                            m = m - 1;
                    }

                    if (zHi > largest / 10.) {
                        allOk = false;
                        fLo = 0;
                        fHi = 0;
                    } else {
                        fLo = intIm.apply(y, zLo, m);
                        fHi = intIm.apply(y, zHi, m);
                    }

                    while (allOk && (fHi * fLo) > 0 && zHi != zLo) {
                        zLo = zHi;
                        zHi = zHi * 1.5;

                        fLo = intIm.apply(y, zLo, m);
                        fHi = intIm.apply(y, zHi, m);

                        if (zHi > largest / 10.0)
                            allOk = false;
                    }

                    if (zHi > largest / 10.0)
                        allOk = false;

                    if (!allOk) {
                        result = 0.0;
                        return result;
                    }

                    zero2 = newtonMethodWithBisectionWithM(y, zLo, zHi, zero, intIm, dk, m);

                    result = gaussQuad(f, zero1, zero2, y, mu);

                    iteration += 1;
                    xVec[iteration] = zero2;
                    SidiAcceleration sidi = new SidiAcceleration(mMatrix, nMatrix, wOld, xVec);
                    sidi.apply(area, result, w, iteration);
                    w = sidi._w;
                    relErr = sidi._relErr;
                    area += result;
                }

                result = area0 + w;
            }
        }
        result = abs(result / PI);

        return result;
    }
}
