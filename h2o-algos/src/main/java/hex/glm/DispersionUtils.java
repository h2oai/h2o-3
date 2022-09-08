package hex.glm;

import hex.DataInfo;
import water.Job;
import water.fvec.Frame;
import water.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
     * This method estimates the tweedie dispersion parameter when variance power > 2.  It will use Newton's update
     * when it is working correctly:  meaning the log likelihood increase with the new dispersion parameter.  However,
     * there are cases where the log likelihood decreases with the new dispersion parameter, in this case, instead
     * of 
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
        double change, dispersionTemp;
        double logLLCurr;
        double logLLNext;
        List<Double> loglikelihoodList = new ArrayList<>();
        List<Double> dispersionList = new ArrayList<>();
        List<Double> llChangeList = new ArrayList<>();

        for (int index = 0; index < parms._max_iterations_dispersion; index++) {
            tDispersion.updateDispersionP(dispersionCurr);
            DispersionTask.ComputeMaxSumSeriesTsk computeTask = new DispersionTask.ComputeMaxSumSeriesTsk(tDispersion,
                    parms, true);
            computeTask.doAll(tDispersion._infoFrame);
            logLLCurr = computeTask._logLL / computeTask._nobsLL;

            // record log values
            loglikelihoodList.add(logLLCurr);
            dispersionList.add(dispersionCurr);
            if (loglikelihoodList.size() > 1) {
                llChangeList.add(loglikelihoodList.get(index) - loglikelihoodList.get(index - 1));
                if ((Math.abs(llChangeList.get(llChangeList.size() - 1)) < parms._dispersion_epsilon)) {
                    tDispersion.cleanUp();
                    return dispersionList.get(index);
                }
            }
            // set new alpha
            change = computeTask._dLogLL / computeTask._d2LogLL; // no line search is employed at the moment ToDo: add line search
            if (Math.abs(change) < 1e-3) { // speed up change if necessary
                change = dispersionLS(computeTask, tDispersion, parms);

                if (!Double.isFinite(change))
                    return dispersionCurr;
                dispersionTemp = dispersionCurr-change;
            } else {
                dispersionNew = dispersionCurr - change;
                if (dispersionNew < 0)
                    dispersionNew = dispersionCurr*0.5;
                tDispersion.updateDispersionP(dispersionNew);
                DispersionTask.ComputeMaxSumSeriesTsk computeTaskNew = new DispersionTask.ComputeMaxSumSeriesTsk(tDispersion,
                        parms, false);
                computeTaskNew.doAll(tDispersion._infoFrame);
                logLLNext = computeTaskNew._logLL / computeTaskNew._nobsLL;
                
                if (logLLNext > logLLCurr) { // there is improvement
                    dispersionTemp = dispersionNew;
                } else {    // heuristics to deal with when change has the wrong sign
                    dispersionTemp = dispersionCurr + parms._dispersion_learning_rate * change;
                }
            }
            
            if (dispersionTemp < 0)
                dispersionCurr *= 0.5;
            else
                dispersionCurr = dispersionTemp;

            if ((index % 100 == 0) && // check for additional stopping conditions for every 100th iterative steps
                    (job.stop_requested() ||  // user requested stop via stop_requested()
                            (System.currentTimeMillis() - currTime) > timeLeft)) { // time taken exceeds model build time
                Log.warn("tweedie dispersion parameter estimation was interrupted by user or due to time out." +
                        "  Estimation process has not converged. Increase your max_runtime_secs if you have set " +
                        "maximum runtime for your model building process.");
                tDispersion.cleanUp();
                return dispersionList.get(loglikelihoodList.indexOf(Collections.max(loglikelihoodList)));
            }
        }
        tDispersion.cleanUp();
        return dispersionCurr;
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
