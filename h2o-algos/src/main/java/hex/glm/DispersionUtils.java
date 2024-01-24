package hex.glm;

import hex.DataInfo;
import water.Job;
import water.Key;
import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.math3.special.Gamma.*;

public class DispersionUtils {
    /***
     * Estimate dispersion factor using maximum likelihood.  I followed section IV of the doc in 
     * https://github.com/h2oai/h2o-3/issues/7013. 
     */
    public static double estimateGammaMLSE(GLMTask.ComputeGammaMLSETsk mlCT, double seOld, double[] beta, 
                                           GLMModel.GLMParameters parms, ComputationState state, Job job, GLMModel model) {
        double constantValue = mlCT._wsum + mlCT._sumlnyiOui - mlCT._sumyiOverui;
        DataInfo dinfo = state.activeData();
        Frame adaptedF = dinfo._adaptedFrame;
        long currTime = System.currentTimeMillis();
        long modelBuiltTime = currTime - model._output._start_time;
        long timeLeft = parms._max_runtime_secs > 0 ? (long) (parms._max_runtime_secs * 1000 - modelBuiltTime) : Long.MAX_VALUE;

        // stopping condition for while loop are:
        // 1. magnitude of iterative change to se < EPS
        // 2. there are more than MAXITERATIONS of updates
        // 2. for every 100th iteration, we check for additional stopping condition:
        //    a.  User requests stop via stop_requested;
        //    b.  User sets max_runtime_sec and that time has been exceeded.
        for (int index=0; index<parms._max_iterations_dispersion; index++) {
            GLMTask.ComputeDiTriGammaTsk ditrigammatsk = new GLMTask.ComputeDiTriGammaTsk(null, dinfo, job._key, beta,
                    parms, seOld).doAll(adaptedF);
            double numerator = mlCT._wsum*Math.log(seOld)-ditrigammatsk._sumDigamma+constantValue; // equation 2 of doc
            double denominator = mlCT._wsum/seOld - ditrigammatsk._sumTrigamma;  // equation 3 of doc
            double change = numerator/denominator;
            if (denominator == 0 || !Double.isFinite(change))
                return seOld;
            if (Math.abs(change) < parms._dispersion_epsilon) // stop if magnitude of iterative updates to se < EPS
                return seOld-change;
            else {
                double se = seOld - change;
                if (se < 0) // heuristic to prevent seInit <= 0
                    seOld *= 0.5;
                else
                    seOld = se;
            }

            if ((index % 100 == 0) && // check for additional stopping conditions for every 100th iterative steps
                    (job.stop_requested() ||  // user requested stop via stop_requested()
                            (System.currentTimeMillis()-currTime) > timeLeft)) { // time taken exceeds GLM building time
                Log.warn("gamma dispersion parameter estimation was interrupted by user or due to time out.  " +
                        "Estimation process has not converged. Increase your max_runtime_secs if you have set maximum" +
                        " runtime for your model building process.");
                return seOld;
            }
        }
        Log.warn("gamma dispersion parameter estimation fails to converge within "+
                parms._max_iterations_dispersion+" iterations.  Increase max_iterations_dispersion or decrease " +
                "dispersion_epsilon.");
        return seOld;
    }

    private static double getTweedieLogLikelihood(GLMModel.GLMParameters parms, DataInfo dinfo, double phi, Vec mu) {
        final double llh = new TweedieEstimator(
                parms._tweedie_variance_power,
                phi,
                false,
                false,
                false,
                false,
                true)
                .compute(mu,
                        dinfo._adaptedFrame.vec(parms._response_column),
                        parms._weights_column == null
                                ? dinfo._adaptedFrame.makeCompatible(new Frame(Vec.makeOne(dinfo._adaptedFrame.numRows())))[0]
                                : dinfo._adaptedFrame.vec(parms._weights_column))
                ._loglikelihood;
        Log.debug("Tweedie LogLikelihood(p=" + parms._tweedie_variance_power + ", phi=" + phi + ") = " + llh);
        return llh;
    }


    private static double goldenRatioDispersionSearch(GLMModel.GLMParameters parms, DataInfo dinfo, Vec mu,
                                                      List<Double> logLikelihoods, List<Double> phis, Job job) {
        // make monotonic
        List<Double> sortedPhis = phis.stream().sorted().collect(Collectors.toList());
        List<Double> sortedLLHs = new ArrayList<>();
        for (int i = 0; i < sortedPhis.size(); i++) {
            double phi = sortedPhis.get(i);
            int index = phis.indexOf(phi);
            sortedLLHs.add(logLikelihoods.get(index));
        }

        // did we already find a region where there is the maximum?
        boolean increasing = true;
        double lowerBound = 1e-16;
        double upperBound = sortedPhis.get(0);
        for (int i = 1; i < sortedPhis.size(); i++) {
            upperBound = sortedPhis.get(i);
            if (sortedLLHs.get(i - 1) > sortedLLHs.get(i)) {
                increasing = false;
                if (i > 2)
                    lowerBound = sortedPhis.get(i - 2);
                else {
                    sortedPhis.add(0, lowerBound);
                    sortedLLHs.add(0, getTweedieLogLikelihood(parms, dinfo, lowerBound, mu));
                }
                break;
            }
        }
        int counter = sortedPhis.size();
        int iterationsLeft = parms._max_iterations_dispersion - 10 * counter;
        while (increasing && iterationsLeft > counter && !job.stop_requested()) { // not yet
            counter++;
            upperBound *= 2;
            sortedPhis.add(upperBound);
            double newLLH = getTweedieLogLikelihood(parms, dinfo, upperBound, mu);
            Log.debug("Tweedie looking for the region containing the max. likelihood; upper bound = " + upperBound + "; llh = " + newLLH);
            sortedLLHs.add(newLLH);
            if (sortedLLHs.get(counter - 2) > sortedLLHs.get(counter - 1)) {
                if (counter > 3)
                    lowerBound = sortedPhis.get(counter - 3);
                Log.debug("Tweedie found the region containing the max. likelihood; phi lower bound = " + lowerBound + "; phi upper bound = " + upperBound);
                break;
            }
        }

        // now we should have the maximum between lowerBound and upperBound
        double d = (upperBound - lowerBound) * 0.618; // (hiPhi - lowPhi)/golden ratio 
        double lowPhi = lowerBound;
        double hiPhi = upperBound;

        double midLoPhi = sortedPhis.get(counter - 2);
        double midLoLLH = sortedLLHs.get(counter - 2);
        double midHiPhi = lowPhi + d;
        double midHiLLH = getTweedieLogLikelihood(parms, dinfo, midHiPhi, mu);
        if (midLoPhi > midHiPhi) {
            midLoPhi = hiPhi - d;
            midLoLLH = getTweedieLogLikelihood(parms, dinfo, midLoPhi, mu);
        }
        assert lowerBound <= midLoPhi;
        assert midLoPhi <= midHiPhi;
        assert midHiPhi <= upperBound;
        for (; counter < iterationsLeft; counter++) {
            Log.info("Tweedie golden-section search[iter=" + counter + ", phis=(" + lowPhi + ", " + midLoPhi +
                    ", " + midHiPhi + ", " + hiPhi + "), likelihoods=(" +
                    "..., " + midLoLLH + ", " + midHiLLH + ", ...)]");
            if (job.stop_requested()) {
                return (hiPhi + lowPhi) / 2;
            }
            if (midHiLLH > midLoLLH) {
                lowPhi = midLoPhi;
            } else {
                hiPhi = midHiPhi;
            }
            d = (hiPhi - lowPhi) * 0.618;  // (hiPhi - lowPhi)/golden ratio
            if (hiPhi - lowPhi < parms._dispersion_epsilon) {
                return (hiPhi + lowPhi) / 2;
            }
            midLoPhi = hiPhi - d;
            midHiPhi = lowPhi + d;
            midLoLLH = getTweedieLogLikelihood(parms, dinfo, midLoPhi, mu);
            midHiLLH = getTweedieLogLikelihood(parms, dinfo, midHiPhi, mu);
        }
        return (hiPhi + lowPhi) / 2;
    }
    
    
    /**
     * This method estimates the tweedie dispersion parameter.  It will use Newton's update if the new update will 
     * increase the loglikelihood.  Otherwise, the dispersion will be updated as 
     *                        dispersionNew = dispersionCurr + learningRate * update.
     * In addition, line search is used to increase the magnitude of the update when the update magnitude is too small
     * (< 1e-3).  
     * 
     * Every 10th iteration it checks if the optimization doesn't diverge. If it looks like it diverged, it uses a
     * different likelihood estimation that should be more accurate (combination of Series and Fourier inversion method)
     * but without gradients. For this reason it will use a Golden section search which doesn't require gradients and
     * has a linear convergence.
     * 
     * For details, please see sections IV.I, IV.II, and IV.III in document here: 
     */
    public static double estimateTweedieDispersionOnly(GLMModel.GLMParameters parms, GLMModel model, Job job,
                                                              double[] beta, DataInfo dinfo) {
        if (parms._tweedie_variance_power >= 2 && 
            dinfo._adaptedFrame.vec(parms._response_column).min() <= 0) {
            Log.warn("Response contains zeros or negative values but "+
                    "Tweedie variance power does not support zeros. "+
                    "Instances with response <= 0 will be skipped.");
            model.addWarning("Response contains zeros or negative values but "+
                    "Tweedie variance power does not support zeros. "+
                    "Instances with response <= 0 will be skipped.");
        }
        DispersionTask.GenPrediction gPred = new DispersionTask.GenPrediction(beta, model, dinfo).doAll(
                1, Vec.T_NUM, dinfo._adaptedFrame);
        Vec mu = Scope.track(gPred.outputFrame(Key.make(), new String[]{"prediction"}, null)).vec(0);
        List<Double> logLikelihoodSanityChecks = new ArrayList<>();
        List<Double> dispersionsSanityChecks = new ArrayList<>();
        logLikelihoodSanityChecks.add(getTweedieLogLikelihood(parms, dinfo, parms._init_dispersion_parameter, mu));
        dispersionsSanityChecks.add(parms._init_dispersion_parameter);
        final double dispersion = goldenRatioDispersionSearch(parms, dinfo, mu, logLikelihoodSanityChecks, dispersionsSanityChecks, job);
        Log.info("Tweedie dispersion estimate = "+dispersion);
        return dispersion;
        
        /*
        // FIXME: The Newton's method seems not to be reproducible on jenkins (runit_GLM_tweedie_ml_dispersion_estimation_only.R)
      
        long timeLeft = parms._max_runtime_secs > 0 ? (long) (parms._max_runtime_secs * 1000 - modelBuiltTime)
            : Long.MAX_VALUE;  
        long currTime = System.currentTimeMillis();

        long modelBuiltTime = currTime - model._output._start_time;
 
        TweedieMLDispersionOnly tDispersion = new TweedieMLDispersionOnly(parms.train(), parms, model, beta, dinfo);

        double dispersionCurr = tDispersion._dispersionParameter;   // initial value of dispersion parameter
        double dispersionNew;
        double update;
        double logLLCurr, logLLNext;
        List<Double> loglikelihoodList = new ArrayList<>();
        List<Double> llChangeList = new ArrayList<>();
        List<Double> dispersionList = new ArrayList<>();
        double bestLogLikelihoodFromSanityCheck = getTweedieLogLikelihood(parms, dinfo,dispersionCurr,mu);
        List<Double> logLikelihoodSanityChecks = new ArrayList<>();
        List<Double> dispersionsSanityChecks = new ArrayList<>();
        logLikelihoodSanityChecks.add(bestLogLikelihoodFromSanityCheck);
        dispersionsSanityChecks.add(dispersionCurr);
        for (int index = 0; index < parms._max_iterations_dispersion; index++) {
            Log.info("Tweedie dispersion ML estimation [iter="+index+", phi="+dispersionCurr+"]");
            tDispersion.updateDispersionP(dispersionCurr);
            DispersionTask.ComputeMaxSumSeriesTsk computeTask = new DispersionTask.ComputeMaxSumSeriesTsk(tDispersion,
                    parms, true);
            computeTask.doAll(tDispersion._infoFrame);
            logLLCurr = computeTask._logLL / computeTask._nobsLL;
            // record loglikelihood values
            loglikelihoodList.add(logLLCurr);
            dispersionList.add(dispersionCurr);
            if (loglikelihoodList.size() > 1) {
                llChangeList.add(loglikelihoodList.get(index) - loglikelihoodList.get(index - 1));
                boolean converged = (Math.abs(llChangeList.get(llChangeList.size() - 1)) < parms._dispersion_epsilon);
                if (index % 10 == 0 || converged) { // do a sanity check once in a while and if we think we converged
                    double newLogLikelihood = getTweedieLogLikelihood(parms, dinfo, dispersionCurr, mu);
                    logLikelihoodSanityChecks.add(newLogLikelihood);
                    dispersionsSanityChecks.add(dispersionCurr);
                    if (newLogLikelihood < bestLogLikelihoodFromSanityCheck) {
                        // we are getting worse.
                        Log.info("Tweedie sanity check FAIL. Trying Golden-section search instead of Newton's method.");
                        tDispersion.cleanUp();
                        final double dispersion = goldenRatioDispersionSearch(parms, dinfo, mu, logLikelihoodSanityChecks, dispersionsSanityChecks, job);
                        Log.info("Tweedie dispersion estimate = "+dispersion);
                        return dispersion;
                    }
                    bestLogLikelihoodFromSanityCheck = Math.max(bestLogLikelihoodFromSanityCheck, newLogLikelihood);
                    Log.debug("Tweedie sanity check OK");
                }

                if (converged) {
                    tDispersion.cleanUp(); // early stop if loglikelihood has'n changed by > parms._dispersion_epsilon
                    Log.info("last dispersion "+dispersionCurr);
                    return dispersionList.get(loglikelihoodList.indexOf(Collections.max(loglikelihoodList)));
                }
            }
            if (loglikelihoodList.size() > 10) {
                if (loglikelihoodList.stream().skip(loglikelihoodList.size() - 3).noneMatch((x) -> x != null && Double.isFinite(x))) {
                    Log.warn("tweedie dispersion parameter estimation got stuck in numerically unstable region.");
                    tDispersion.cleanUp();
                    // If there's NaN Collections.max picks it
                    return Double.NaN;
                }
            }


            // get new update to dispersion
            update = computeTask._dLogLL / computeTask._d2LogLL;
            if (Math.abs(update) < 1e-3) { // line search for speedup and increase magnitude of change
                update = dispersionLS(computeTask, tDispersion, parms);
                if (!Double.isFinite(update)) {
                    Log.info("last dispersion "+dispersionCurr);
                    return dispersionList.get(loglikelihoodList.indexOf(Collections.max(loglikelihoodList)));
                }
                dispersionNew = dispersionCurr - update;
            } else {
                dispersionNew = dispersionCurr - update;
                if (dispersionNew < 0)
                    dispersionNew = dispersionCurr*0.5;
                tDispersion.updateDispersionP(dispersionNew);
                DispersionTask.ComputeMaxSumSeriesTsk computeTaskNew = new DispersionTask.ComputeMaxSumSeriesTsk(tDispersion,
                        parms, false);
                computeTaskNew.doAll(tDispersion._infoFrame);
                logLLNext = computeTaskNew._logLL / computeTaskNew._nobsLL;
                
                if (logLLNext <= logLLCurr)
                    dispersionNew = dispersionCurr + parms._dispersion_learning_rate * update;
            }
            
            if (dispersionNew < 0)
                dispersionCurr *= 0.5;
            else
                dispersionCurr = dispersionNew;

            if ((index % 100 == 0) && // check for additional stopping conditions for every 100th iterative steps
                    (job.stop_requested() ||  // user requested stop via stop_requested()
                            (System.currentTimeMillis() - currTime) > timeLeft)) { // time taken exceeds model build time
                Log.warn("tweedie dispersion parameter estimation was interrupted by user or due to time out." +
                        "  Estimation process has not converged. Increase your max_runtime_secs if you have set " +
                        "maximum runtime for your model building process.");
                tDispersion.cleanUp();
                Log.info("last dispersion "+dispersionCurr);
                return dispersionList.get(loglikelihoodList.indexOf(Collections.max(loglikelihoodList)));
            }
        }
        tDispersion.cleanUp();
        if (dispersionList.size()>0) {
            Log.info("last dispersion "+dispersionCurr);
            return dispersionList.get(loglikelihoodList.indexOf(Collections.max(loglikelihoodList)));
        }
        else
            return dispersionCurr;
            
         */
    }

    static class NegativeBinomialGradientAndHessian extends MRTask<NegativeBinomialGradientAndHessian> {
        double _grad;
        double _hess;
        double _theta;
        double _invTheta;
        double _invThetaSq;
        double _llh;

        NegativeBinomialGradientAndHessian(double theta) {
            assert theta > 0;
            _theta = theta;
            _invTheta = 1./theta;
            _invThetaSq = _invTheta*_invTheta;
        }

        @Override
        public void map(Chunk[] cs) {
            // mu, y, w
            for (int i = 0; i < cs[0]._len; i++) {
                final double mu = cs[0].atd(i);
                final double y = cs[1].atd(i);
                final double w = cs[2].atd(i);
                _grad += w * (
                        -mu*(y+_invTheta)/(mu*_theta+1) +
                                (
                                        y +
                                                (
                                                        Math.log(mu*_theta + 1) -
                                                                digamma(y+_invTheta) +
                                                                digamma(_invTheta)
                                                ) * _invTheta

                                ) * _invTheta
                );
                _hess += w * (
                        (mu*mu*(y+_invTheta)/Math.pow(mu*_theta+1, 2)) +
                                (-y +
                                        (2 * mu) / (mu*_theta+1) +
                                        ((-2 * Math.log(mu*_theta + 1)) +
                                                2*digamma(y + _invTheta) -
                                                2*digamma(_invTheta) +
                                                (
                                                        trigamma(y+_invTheta) -
                                                                trigamma(_invTheta)
                                                ) * _invTheta
                                        ) * _invTheta
                                ) * _invThetaSq
                );
                _llh += logGamma(y + _invTheta) - logGamma(_invTheta) - logGamma(y + 1) +
                        y * Math.log(_theta * mu) - (y+_invTheta) * Math.log(1 + _theta * mu);
            }
        }

        @Override
        public void reduce(NegativeBinomialGradientAndHessian mrt) {
            _grad += mrt._grad;
            _hess += mrt._hess;
            _llh += mrt._llh;
        }
    };

    static class CalculateNegativeBinomialScoreAndInfo extends MRTask<CalculateNegativeBinomialScoreAndInfo> {
        double _score;
        double _info;
        double _theta;

        CalculateNegativeBinomialScoreAndInfo(double theta) {
            _theta = theta;
        }

        @Override
        public void map(Chunk[] cs) {
            // mu, y, w
            for (int i = 0; i < cs[0]._len; i++) {
                final double w = cs[2].atd(i);
                _score += w * (digamma(_theta + cs[1].atd(i)) - digamma(_theta) + Math.log(_theta) + 1 -
                        Math.log(_theta + cs[0].atd(i)) - (cs[1].atd(i) + _theta) / (cs[0].atd(i) + _theta));
                _info += w * (-trigamma(_theta + cs[1].atd(i)) + trigamma(_theta) -
                        1/_theta + 2/(cs[0].atd(i) + _theta) - (cs[1].atd(i) + _theta)/
                        Math.pow(cs[0].atd(i) + _theta, 2));
            }
        }

        @Override
        public void reduce(CalculateNegativeBinomialScoreAndInfo mrt) {
            _score += mrt._score;
            _info += mrt._info;
        }
    };

    static class CalculateInitialTheta extends MRTask<CalculateInitialTheta> {
        double _theta0;
        @Override
        public void map(Chunk[] cs) {
            // mu, y, w
            for (int i = 0; i < cs[0]._len; i++) {
                _theta0 += cs[2].atd(i) * Math.pow(cs[1].atd(i)/cs[0].atd(i) - 1, 2);
            }
        }

        @Override
        public void reduce(CalculateInitialTheta mrt) {
            _theta0 += mrt._theta0;
        }
    };

    public static double estimateNegBinomialDispersionMomentMethod(GLMModel model, double[] beta, DataInfo dinfo, Vec weights, Vec response, Vec mu) {
        class MomentMethodThetaEstimation extends MRTask<MomentMethodThetaEstimation> {
            double _muSqSum;
            double _sSqSum;
            double _muSum;
            double _wSum;

            @Override
            public void map(Chunk[] cs) {
                // mu, y, w
                for (int i = 0; i < cs[0]._len; i++) {
                    final double w = cs[2].atd(i);
                    _muSqSum += w * Math.pow(cs[0].atd(i), 2);
                    _sSqSum += w * Math.pow(cs[1].atd(i) - cs[0].atd(i), 2);
                    _muSum += w * cs[0].atd(i);
                    _wSum += w;
                }
            }

            @Override
            public void reduce(MomentMethodThetaEstimation mrt) {
                _muSqSum += mrt._muSqSum;
                _sSqSum += mrt._sSqSum;
                _muSum += mrt._muSum;
                _wSum += mrt._wSum;
            }
        }
        ;
        MomentMethodThetaEstimation mm = new MomentMethodThetaEstimation().doAll(mu, response, weights);

        return mm._muSqSum / (mm._sSqSum - mm._muSum / mm._wSum);
    }


    public static double estimateNegBinomialDispersionFisherScoring(GLMModel.GLMParameters parms, GLMModel model,
                                                       double[] beta, DataInfo dinfo) {
        Vec weights = dinfo._weights
                ? dinfo.getWeightsVec()
                : dinfo._adaptedFrame.makeCompatible(new Frame(Vec.makeOne(dinfo._adaptedFrame.numRows())))[0];

        final double nRows = weights == null
                ? dinfo._adaptedFrame.numRows()
                : weights.mean() * weights.length();

        DispersionTask.GenPrediction gPred = new DispersionTask.GenPrediction(beta, model, dinfo).doAll(
                1, Vec.T_NUM, dinfo._adaptedFrame);
        Vec mu = gPred.outputFrame(Key.make(), new String[]{"prediction"}, null).vec(0);
        Vec response = dinfo._adaptedFrame.vec(dinfo.responseChunkId(0));
        double invTheta = nRows / new CalculateInitialTheta().doAll(mu, response, weights)._theta0;
        double delta = 1;
        int i = 0;
        for (; i < parms._max_iterations_dispersion; i++) {
            if (Math.abs(delta) < parms._dispersion_epsilon) break;
            invTheta = Math.abs(invTheta);
            CalculateNegativeBinomialScoreAndInfo si = new CalculateNegativeBinomialScoreAndInfo(invTheta).doAll(mu, response, weights);
            delta = si._score/si._info;
            invTheta += delta;
        }

        if (invTheta < 0)
            Log.warn("Dispersion estimate truncated at zero.");
        if (i == parms._max_iterations_dispersion)
            Log.warn("Iteration limit reached.");
        return 1./invTheta;
    }
    
    public static double dispersionLS(DispersionTask.ComputeMaxSumSeriesTsk computeTsk,
                                      TweedieMLDispersionOnly tDispersion, GLMModel.GLMParameters parms) {
        double currObj = Double.NEGATIVE_INFINITY;
        double newObj;
        double dispersionCurr = tDispersion._dispersionParameter;
        double dispersionNew;
        double update = computeTsk._dLogLL/computeTsk._d2LogLL;
        for (int index=0; index < parms._max_iterations_dispersion; index++){
            if (Double.isFinite(update)) {
                dispersionNew = dispersionCurr-update;
                tDispersion.updateDispersionP(dispersionNew);
                DispersionTask.ComputeMaxSumSeriesTsk computeTskNew = new DispersionTask.ComputeMaxSumSeriesTsk(tDispersion,
                        parms, false).doAll(tDispersion._infoFrame);
                newObj = computeTskNew._logLL/computeTskNew._nobsLL;
                if (newObj > currObj) {
                    currObj = newObj;
                    update = 2*update;
                } else {
                    return update;
                }
            } else {
                return Double.NaN;
            }
        }
        return update;
    }
    
    public static double[] makeZeros(double[] sourceCoeffs, double[] targetCoeffs) {
        int size = targetCoeffs.length;
        for (int valInd = 0; valInd < size; valInd++)
            targetCoeffs[valInd] = targetCoeffs[valInd]-sourceCoeffs[valInd];
        return targetCoeffs;
    }
}
