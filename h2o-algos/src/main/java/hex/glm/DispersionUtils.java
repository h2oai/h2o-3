package hex.glm;

import hex.DataInfo;
import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.commons.math3.special.Gamma.*;

public class DispersionUtils {
    /***
     * Estimate dispersion factor using maximum likelihood.  I followed section IV of the doc in 
     * https://h2oai.atlassian.net/browse/PUBDEV-8683 . 
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

    /**
     * This method estimates the tweedie dispersion parameter.  It will use Newton's update if the new update will 
     * increase the loglikelihood.  Otherwise, the dispersion will be updated as 
     *                        dispersionNew = dispersionCurr + learningRate * update.
     * In addition, line search is used to increase the magnitude of the update when the update magnitude is too small
     * (< 1e-3).  
     * 
     * For details, please see seciton IV.I, IV.II, and IV.III in document here: 
     */
    public static double estimateTweedieDispersionOnly(GLMModel.GLMParameters parms, GLMModel model, Job job,
                                                              double[] beta, DataInfo dinfo) {
        long currTime = System.currentTimeMillis();
        long modelBuiltTime = currTime - model._output._start_time;
        long timeLeft = parms._max_runtime_secs > 0 ? (long) (parms._max_runtime_secs * 1000 - modelBuiltTime)
                : Long.MAX_VALUE;
        TweedieMLDispersionOnly tDispersion = new TweedieMLDispersionOnly(parms.train(), parms, model, beta, dinfo);
        double dispersionCurr = tDispersion._dispersionParameter;   // initial value of dispersion parameter
        double dispersionNew;
        double update;
        double logLLCurr, logLLNext;
        List<Double> loglikelihoodList = new ArrayList<>();
        List<Double> llChangeList = new ArrayList<>();
        List<Double> dispersionList = new ArrayList<>();

        for (int index = 0; index < parms._max_iterations_dispersion; index++) {
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
                if ((Math.abs(llChangeList.get(llChangeList.size() - 1)) < parms._dispersion_epsilon)) {
                    tDispersion.cleanUp(); // early stop if loglikelihood has'n changed by > parms._dispersion_epsilon
                    Log.info("last dispersion "+dispersionCurr);
                    return dispersionList.get(loglikelihoodList.indexOf(Collections.max(loglikelihoodList)));
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
    }

    static class NegativeBinomialGradientAndHessian extends MRTask<NegativeBinomialGradientAndHessian> {
        double _grad;
        double _hess;
        double _theta;
        double _invTheta;
        double _llh;

        NegativeBinomialGradientAndHessian(double theta) {
            _theta = theta;
            _invTheta = 1./theta;
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
                                ) * _invTheta * _invTheta

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

    public static double estimateNegBinomialDispersionMomentMethod(GLMModel.GLMParameters parms, GLMModel model, Job job,
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
        };
        MomentMethodThetaEstimation mm = new MomentMethodThetaEstimation().doAll(mu, response, weights);

        return mm._muSqSum/(mm._sSqSum - mm._muSum/mm._wSum);
    }


    public static double estimateNegBinomialDispersionFisherScoring(GLMModel.GLMParameters parms, GLMModel model, Job job,
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
        double theta = nRows / new CalculateInitialTheta().doAll(mu, response, weights)._theta0;
        double delta = 1;
        int i = 0;
        for (; i < parms._max_iterations_dispersion; i++) {
            if (Math.abs(delta) < parms._dispersion_epsilon) break;
            theta = Math.abs(theta);
            CalculateNegativeBinomialScoreAndInfo si = new CalculateNegativeBinomialScoreAndInfo(theta).doAll(mu, response, weights);
            delta = si._score/si._info;
            theta += delta;
        }

        if (theta < 0)
            Log.warn("Dispersion estimate truncated at zero.");
        if (i == parms._max_iterations_dispersion)
            Log.warn("Iteration limit reached.");
        return theta;
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
