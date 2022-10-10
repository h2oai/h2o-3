package hex.glm;

import hex.DataInfo;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.util.FrameUtils;
import water.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static hex.glm.DispersionTask.ConstColNames.*;
import static hex.glm.DispersionTask.InfoColNames.*;
import static org.apache.commons.math3.special.Gamma.logGamma;

public class DispersionTask {
    public static final int RESPIND = 0;
    public static final int MUIND = 1;
    public static final int WEIGHTIND = 2;

    public enum ConstColNames {
        JMaxConst, zConst, LogPart2Const, LogOneOverY, LogOneOverPiY, FirstOrderDerivConst,
        SecondOrderDerivConst
    }

    ;

    public enum InfoColNames {
        MaxValIndex, LOGZ, LOGWVMax, LOGDWVMax, LOGD2WVMax, JkL, JkU, DjkL, DjkU, D2jkL, D2jkU,
        SumWV, SumDWV, SumD2WV, LL, DLL, D2LL
    }

    ;

    /***
     * Class to pre-calculate constants assocated with the following processes:
     * 1. maximum term index: jMaxConst (col 0) different between p < 2 and p > 2
     * 2. constant term associated with z: zConst, different between p < 2 and p > 2
     * 3. log likelihood: part2Const (col 1), same for all p
     * 4. log 1/y for 1<p<2 
     * 5. log 1/(Pi*y) for p>2
     * 5. dlogf/dphi firstOrderDerivConst, same for all p
     * 6. d2logf/dphi2 secondOrderDerivConst, same for all p
     * In addition, we also have maximum term with maximum index: logMaxConst, not part of constFrame.
     */
    public static class ComputeTweedieConstTsk extends MRTask<ComputeTweedieConstTsk> {
        double _variancePower;
        double _alpha;
        boolean _weightPresent;
        double _twoMinusP;
        double _oneOver2MinusP;
        double _oneMinusP;
        double _oneOver1MinusP;
        double _oneOverPi;
        double _pMinusOne;
        double _oneMinusAlpha;


        public ComputeTweedieConstTsk(double vPower, Frame infoFrame) {
            _variancePower = vPower;
            _alpha = (2.0 - vPower) / (1.0 - vPower);
            _weightPresent = infoFrame.numCols() > 2;
            _twoMinusP = 2 - vPower;
            _oneOver2MinusP = 1.0 / _twoMinusP;
            _oneMinusP = 1 - _variancePower;
            _oneOver1MinusP = 1.0 / _oneMinusP;
            _oneOverPi = 1.0 / Math.PI;
            _pMinusOne = _variancePower - 1;
            _oneMinusAlpha = 1.0 - _alpha;
        }

        public void map(Chunk[] chks, NewChunk[] constChks) {
            Map<ConstColNames, Integer> constColName2Ind = new HashMap<>();
            ComputeMaxSumSeriesTsk.setConstIndices(constColName2Ind, 0);
            int chkLen = chks[0].len();
            for (int rowInd = 0; rowInd < chkLen; rowInd++) {
                // calculate jMaxConst
                calJMaxConst(chks, constChks, rowInd, constColName2Ind.get(JMaxConst));
                // calculate zConst
                calZConst(chks, constChks, rowInd, constColName2Ind.get(zConst));
                // calculate part2Const for ll
                calPart2Const(chks, constChks, rowInd, constColName2Ind.get(LogPart2Const));
                // calculate part1Const, the 1/y
                calPart1LogConst(chks, constChks, rowInd, constColName2Ind.get(LogOneOverY));
                // calculate partConst, 1/(PI*y)
                calPart1LogPIConst(chks, constChks, rowInd, constColName2Ind.get(LogOneOverPiY));
                // calculate constants for derivatives
                calDerivConst(chks, constChks, rowInd,
                        new int[]{constColName2Ind.get(FirstOrderDerivConst),
                                constColName2Ind.get(ConstColNames.SecondOrderDerivConst)});
            }
        }

        public void calZConst(Chunk[] chks, NewChunk[] constChks, int rowInd, int newChkColInd) {
            double response = chks[RESPIND].atd(rowInd);
            if (Double.isFinite(response)) {
                if (response > 0) {
                    double val = _variancePower < 2
                            ? Math.pow(response, -_alpha) * Math.pow(_pMinusOne, _alpha) * _oneOver2MinusP
                            : -Math.pow(response, -_alpha) * Math.pow(_pMinusOne, _alpha) * _oneOver2MinusP;
                    if (_weightPresent)
                        val *= Math.pow(chks[WEIGHTIND].atd(rowInd), _oneMinusAlpha);
                    constChks[newChkColInd].addNum(val);
                } else
                    constChks[newChkColInd].addNum(0);
            } else {
                constChks[newChkColInd].addNA();
            }
        }

        public void calDerivConst(Chunk[] chks, NewChunk[] constChks, int rowInd, int[] newChkColInd) {
            double response = chks[RESPIND].atd(rowInd);
            double mu = chks[MUIND].atd(rowInd);
            double val;
            double weight = _weightPresent ? chks[WEIGHTIND].atd(rowInd) : 1;
            if (Double.isFinite(response) && Double.isFinite(mu)) {
                val = -response * Math.pow(mu, _oneMinusP) * _oneOver1MinusP + Math.pow(mu, _twoMinusP) * _oneOver2MinusP;
                val *= weight * weight;
                constChks[newChkColInd[0]].addNum(val); // dll/dphi constant
                val *= -2 * weight;
                constChks[newChkColInd[1]].addNum(val); // d2ll/dphi2 constant
            } else {
                constChks[newChkColInd[0]].addNA();
                constChks[newChkColInd[1]].addNA();
            }
        }

        public void calPart1LogConst(Chunk[] chks, NewChunk[] constChks, int rowInd, int newChkColInd) {
            double response = chks[RESPIND].atd(rowInd);
            if (Double.isFinite(response) && response > 0) {
                constChks[newChkColInd].addNum(Math.log(1.0 / response));
            } else {
                constChks[newChkColInd].addNA();
            }
        }

        public void calPart1LogPIConst(Chunk[] chks, NewChunk[] constChks, int rowInd, int newChkColInd) {
            double response = chks[RESPIND].atd(rowInd);
            if (Double.isFinite(response) && response > 0) {
                constChks[newChkColInd].addNum(Math.log(_oneOverPi / response));
            } else {
                constChks[newChkColInd].addNA();
            }
        }

        public void calPart2Const(Chunk[] chks, NewChunk[] constChks, int rowInd, int newChkColInd) {
            double response = chks[RESPIND].atd(rowInd);
            double mu = chks[MUIND].atd(rowInd);
            if (Double.isFinite(response) && Double.isFinite(mu)) {
                double val;
                val = -Math.pow(mu, _twoMinusP) * _oneOver2MinusP;
                if (response > 0)
                    val += response * Math.pow(mu, _oneMinusP) * _oneOver1MinusP;
                if (_weightPresent)
                    val *= chks[WEIGHTIND].atd(rowInd);
                constChks[newChkColInd].addNum(val);
            } else {
                constChks[newChkColInd].addNA();
            }
        }

        public void calJMaxConst(Chunk[] chks, NewChunk[] constChks, int rowInd, int newChkColInd) {
            double response = chks[RESPIND].atd(rowInd);
            double mu = chks[MUIND].atd(rowInd);
            if (Double.isFinite(response) && Double.isFinite(mu) && response > 0) {
                double val = _variancePower < 2 ? Math.pow(response, _twoMinusP) * _oneOver2MinusP :
                        -Math.pow(response, _twoMinusP) * _oneOver2MinusP;
                if (_weightPresent)
                    val *= chks[WEIGHTIND].atd(rowInd);
                constChks[newChkColInd].addNum(val);
            } else {
                constChks[newChkColInd].addNA();
            }
        }
    }

    /***
     * This class will compute the following for every row of the dataset:
     * 1. index of maximum magnitude of infinite series;
     * 2. log(z)
     * 3. W or V maximum
     * 5. dW or dV maximum
     * 6. d2W or d2V maximum
     * 7. KL or JL for W or V
     * 8. KU or JU for W or V
     * 9. KL or JL for dW or dV
     * 10. KU or JU for dW or dV
     * 11. KL or JL for d2W or d2V
     * 12. KU or JU for d2W or d2V
     * 13. log likelihood
     * 14. dlog likelihood / d phi
     * 15. d2log likelihood / d2 phi 
     */
    public static class ComputeMaxSumSeriesTsk extends MRTask<ComputeMaxSumSeriesTsk> {
        double _variancePower;
        double _dispersionParameter;
        double _alpha;
        boolean _weightPresent;
        Frame _infoFrame;
        int _constColOffset;
        int _workColOffset;
        int _nWorkCols;
        double _oneOverPhiPower;
        double _oneMinusAlpha;
        double _oneOverPhiSquare;
        double _oneOverPhi3;
        double _logLL;
        double _dLogLL;
        double _d2LogLL;
        boolean _debugOn;
        double _oneOverDispersion;
        double _alphaMinus1TLogDispersion;
        double _alphaTimesPI;
        double _alphaMinus1OverPhi;
        double _alphaMinus1SquareOverPhiSquare;
        int _nWVs = 3;
        int _indexBound;
        double _logDispersionEpsilon;
        boolean[] _computationAccuracy; // set to false when upper bound exceeds _indexBound
        int _constantColumnNumber;
        long _nobsLL;
        long _nobsDLL;
        long _nobsD2LL;
        long _nobNegSum;
        final boolean _calAll;  // if false, speed up task to calculate loglikelihood only and skip other computations

        public ComputeMaxSumSeriesTsk(TweedieMLDispersionOnly tdispersion, GLMModel.GLMParameters parms, boolean calALL) {
            _variancePower = tdispersion._variancePower;
            _dispersionParameter = tdispersion._dispersionParameter;
            _alpha = (2.0 - _variancePower) / (1.0 - _variancePower);
            _weightPresent = tdispersion._weightPresent;
            _infoFrame = tdispersion._infoFrame;
            _nWorkCols = tdispersion._nWorkingCol;
            _constantColumnNumber = tdispersion._constFrameNames.length;
            _constColOffset = _infoFrame.numCols() - _nWorkCols - tdispersion._constNCol;
            _workColOffset = _infoFrame.numCols() - _nWorkCols;
            _oneMinusAlpha = 1 - _alpha;
            _oneOverPhiPower = 1.0 / Math.pow(_dispersionParameter, _oneMinusAlpha);
            _oneOverPhiSquare = 1.0 / (_dispersionParameter * _dispersionParameter);
            _oneOverPhi3 = _oneOverPhiSquare / _dispersionParameter;
            _debugOn = parms._debugTDispersionOnly;
            _oneOverDispersion = 1 / _dispersionParameter;
            _alphaMinus1TLogDispersion = (_alpha - 1) * Math.log(_dispersionParameter);
            _alphaTimesPI = _alpha * Math.PI;
            _indexBound = parms._max_series_index;
            _logDispersionEpsilon = Math.log(parms._tweedie_epsilon);
            _computationAccuracy = new boolean[_nWVs];
            _alphaMinus1OverPhi = (_alpha - 1) / _dispersionParameter;
            _alphaMinus1SquareOverPhiSquare = _alphaMinus1OverPhi * _alphaMinus1OverPhi;
            _calAll = calALL;
        }

        public static void setInfoIndices(Map<InfoColNames, Integer> infoColName2Ind, int constOffset, boolean weightPresent) {
            int offset = weightPresent ? 3 : 2;
            InfoColNames[] infoC = InfoColNames.values();
            offset += constOffset;
            int infoColLen = infoC.length;
            for (int index = 0; index < infoColLen; index++) {
                infoColName2Ind.put(infoC[index], index + offset);
            }
        }

        public static void setConstIndices(Map<ConstColNames, Integer> constColName2Ind, int offset) {
            ConstColNames[] constVal =
                    ConstColNames.values();
            int constantColNum = constVal.length;
            for (int index = 0; index < constantColNum; index++)
                constColName2Ind.put(constVal[index], index + offset);
        }

        public void map(Chunk[] chks) {
            int chkLen = chks[0].len();
            int jKIndMax = 0, jKL = 0, jKU = 0, djKL = 0, djKU = 0, d2jKL = 0, d2jKU = 0;
            double wvMax = 0, dwvMax = 0, d2wvMax = 0, logZ = 0, sumWVj = 0, sumDWVj = 0, sumD2WVj = 0, oneOverSumWVj = 0;
            _logLL = 0;
            _dLogLL = 0;
            _d2LogLL = 0;
            _nobsLL = 0;
            _nobsDLL = 0;
            _nobsD2LL = 0;
            _nobNegSum = 0;
            double tempLL = 0, tempDLL = 0, tempD2LL = 0;
            Map<ConstColNames, Integer> constColName2Ind = new HashMap<>();
            Map<InfoColNames, Integer> infoColName2Ind = new HashMap<>();
            setConstIndices(constColName2Ind, _weightPresent ? 3 : 2);
            setInfoIndices(infoColName2Ind, constColName2Ind.size(), _weightPresent);
            double weight;
            for (int rInd = 0; rInd < chkLen; rInd++) {
                double response = chks[0].atd(rInd);
                weight = _weightPresent ? chks[WEIGHTIND].atd(rInd) : 1;
                if (response >= 0) { // this part is only valid for response >= 0
                    if (response > 0) {
                        // calculate maximum index of series;
                        jKIndMax = findMaxTermIndex(chks, rInd, constColName2Ind.get(JMaxConst));
                        // calculate log(z)
                        logZ = calLogZ(chks, rInd, constColName2Ind.get(zConst));
                        // calculate maximum of Wj/Vk, dWj/dVk, d2Wj/dVk2 without derivative constants of (alpha-1)/Phi
                        // and without 1/y or 1/(PI*y)
                        wvMax = calLogWVMax(chks, rInd, jKIndMax, logZ);
                        double logjKIndMax = Math.log(jKIndMax);
                        dwvMax = wvMax + logjKIndMax;
                        d2wvMax = dwvMax + logjKIndMax;

                        // locate jL/kL, jU/kU for W/V, dW/dV, d2W/dV2;
                        jKL = estimateLowerBound(jKIndMax, wvMax, logZ, new EvalLogWVEnv());
                        jKU = estimateUpperBound(jKIndMax, wvMax, logZ, 0, new EvalLogWVEnv());
                        if (_calAll) {
                            djKL = estimateLowerBound(jKIndMax, dwvMax, logZ, new EvalLogDWVEnv());
                            djKU = estimateUpperBound(jKIndMax, dwvMax, logZ, 1, new EvalLogDWVEnv());
                            d2jKL = estimateLowerBound(jKIndMax, d2wvMax, logZ, new EvalLogD2WVEnv());
                            d2jKU = estimateUpperBound(jKIndMax, d2wvMax, logZ, 2, new EvalLogD2WVEnv());
                        }

                        // sum the series W, dW, d2W but not include 1/y or 1/(PI*y)
                        sumWVj = sumWV(jKL, jKU, wvMax, logZ, new EvalLogWVEnv());
                        if (sumWVj <= 0.0)
                            _nobNegSum++;
                    }
                    if (sumWVj > 0) {
                        tempLL = evalLogLikelihood(chks, rInd, sumWVj, constColName2Ind);
                        if (Double.isFinite(tempLL)) {
                            _logLL += tempLL;
                            _nobsLL += weight;
                        }
                        if (_calAll) {
                            if (response > 0) {
                                oneOverSumWVj = 1.0 / sumWVj;
                                sumDWVj = sumWV(djKL, djKU, dwvMax, logZ, new EvalLogDWVEnv()) * _alphaMinus1OverPhi;
                                sumD2WVj = sumWV(d2jKL, d2jKU, d2wvMax, logZ, new EvalLogD2WVEnv()) * _alphaMinus1SquareOverPhiSquare
                                        - sumDWVj * _oneOverDispersion;
                            }
                            tempDLL = evalDlldPhi(chks, rInd, sumDWVj, oneOverSumWVj, constColName2Ind);
                            if (Double.isFinite(tempDLL)) {
                                _dLogLL += tempDLL;
                                _nobsDLL += weight;
                            }
                            tempD2LL = evalD2lldPhi2(chks, rInd, sumDWVj, sumD2WVj, oneOverSumWVj, constColName2Ind);
                            if (Double.isFinite(tempD2LL)) {
                                _d2LogLL += tempD2LL;
                                _nobsD2LL += weight;
                            }
                        }
                    }
                }

                if (_debugOn)
                    setDebugValues(rInd, jKIndMax, logZ, wvMax, dwvMax, d2wvMax, jKL, jKU, djKL, djKU, d2jKL, d2jKU,
                            sumWVj, sumDWVj, sumD2WVj, tempLL, tempDLL, tempD2LL, chks, infoColName2Ind, response);
            }
            if (_debugOn && _variancePower > 2)
                Log.info("Chunk IDX " + chks[0].cidx() + " contains " + _nobNegSum + " rows of data with series" +
                        " sum < 0.");
        }

        public void setDebugValues(int rInd, int jkIndMax, double logZ, double wvMax, double dwvMax, double d2wvMax,
                                   int jKL, int jKU, int djKL, int djKU, int d2jKL, int d2jKU, double sumWV,
                                   double sumDWV, double sumD2WV, double ll, double dll, double d2ll, Chunk[] chks,
                                   Map<InfoColNames, Integer> infoColName2Ind, double response) {
            if (response == 0) {
                chks[infoColName2Ind.get(MaxValIndex)].set(rInd, 0);
                chks[infoColName2Ind.get(LOGZ)].set(rInd, 0);
                chks[infoColName2Ind.get(LOGWVMax)].set(rInd, 0);
                chks[infoColName2Ind.get(LOGDWVMax)].set(rInd, 0);
                chks[infoColName2Ind.get(LOGD2WVMax)].set(rInd, 0);
                chks[infoColName2Ind.get(JkL)].set(rInd, 0);
                chks[infoColName2Ind.get(JkU)].set(rInd, 0);
                chks[infoColName2Ind.get(DjkL)].set(rInd, 0);
                chks[infoColName2Ind.get(DjkU)].set(rInd, 0);
                chks[infoColName2Ind.get(D2jkL)].set(rInd, 0);
                chks[infoColName2Ind.get(D2jkU)].set(rInd, 0);
                chks[infoColName2Ind.get(SumWV)].set(rInd, 0);
                chks[infoColName2Ind.get(SumDWV)].set(rInd, 0);
                chks[infoColName2Ind.get(SumD2WV)].set(rInd, 0);
                chks[infoColName2Ind.get(LL)].set(rInd, ll);
                chks[infoColName2Ind.get(DLL)].set(rInd, dll);
                chks[infoColName2Ind.get(D2LL)].set(rInd, d2ll);
            } else {
                chks[infoColName2Ind.get(MaxValIndex)].set(rInd, jkIndMax);
                chks[infoColName2Ind.get(LOGZ)].set(rInd, logZ);
                chks[infoColName2Ind.get(LOGWVMax)].set(rInd, wvMax);
                chks[infoColName2Ind.get(LOGDWVMax)].set(rInd, dwvMax);
                chks[infoColName2Ind.get(LOGD2WVMax)].set(rInd, d2wvMax);
                chks[infoColName2Ind.get(JkL)].set(rInd, jKL);
                chks[infoColName2Ind.get(JkU)].set(rInd, jKU);
                chks[infoColName2Ind.get(DjkL)].set(rInd, djKL);
                chks[infoColName2Ind.get(DjkU)].set(rInd, djKU);
                chks[infoColName2Ind.get(D2jkL)].set(rInd, d2jKL);
                chks[infoColName2Ind.get(D2jkU)].set(rInd, d2jKU);
                chks[infoColName2Ind.get(SumWV)].set(rInd, sumWV);
                chks[infoColName2Ind.get(SumDWV)].set(rInd, sumDWV);
                chks[infoColName2Ind.get(SumD2WV)].set(rInd, sumD2WV);
                chks[infoColName2Ind.get(LL)].set(rInd, ll);
                chks[infoColName2Ind.get(DLL)].set(rInd, dll);
                chks[infoColName2Ind.get(D2LL)].set(rInd, d2ll);
            }
        }

        @Override
        public void reduce(ComputeMaxSumSeriesTsk other) {
            this._logLL += other._logLL;
            this._dLogLL += other._dLogLL;
            this._d2LogLL += other._d2LogLL;
            this._nobsLL += other._nobsLL;
            this._nobsDLL += other._nobsDLL;
            this._nobsD2LL += other._nobsD2LL;
            this._nobNegSum += other._nobNegSum;
        }

        @Override
        public void postGlobal() {
            if (_variancePower > 2 && _debugOn)
                Log.info("number of data rows with negative sum " + _nobNegSum);
        }

        public int estimateLowerBound(int jOrkMax, double logWorVmax, double logZ, CalWVdWVd2WV cVal) {
            if (jOrkMax == 1)   // small speedup
                return 1;
            double logWV1 = cVal.calculate(1, _alpha, logZ, logWorVmax, _variancePower);
            if ((logWV1 - logWorVmax) >= _logDispersionEpsilon)
                return 1;
            else {   // call recursive function
                int indexLow = 1;
                int indexHigh = jOrkMax;
                int indexMid = (int) Math.round(0.5 * (indexLow + indexHigh));
                double logVal;
                while ((indexLow < indexHigh) && (indexHigh != indexMid) && (indexLow != indexMid)) {
                    logVal = cVal.calculate(indexMid, _alpha, logZ, logWorVmax, _variancePower);
                    if (logVal - logWorVmax < _logDispersionEpsilon)
                        indexLow = indexMid;
                    else
                        indexHigh = indexMid;
                    indexMid = (int) Math.round(0.5 * (indexLow + indexHigh));
                }
                if (cVal.calculate(indexLow, _alpha, logZ, logWorVmax, _variancePower)-logWorVmax < _logDispersionEpsilon)
                    return indexLow;
                else
                    return indexMid; // difference between indexLow and indexHigh is only 1
            }
        }

        public int estimateUpperBound(int jOrkMax, double logWorVmax, double logZ, int wvIndex, CalWVdWVd2WV cVal) {
            double logWj = cVal.calculate(_indexBound, _alpha, logZ, logWorVmax, _variancePower);
            if ((logWj - logWorVmax) > _logDispersionEpsilon) {
                _computationAccuracy[wvIndex] = false;
                return _indexBound;
            } else {
                int indexLow = jOrkMax;
                int indexHigh = _indexBound;
                int indexMid = (int) Math.round(0.5 * (indexLow + indexHigh));
                while ((indexLow < indexHigh) && (indexHigh != indexMid) && (indexLow != indexMid)) {
                    logWj = cVal.calculate(indexMid, _alpha, logZ, logWorVmax, _variancePower);
                    if (logWj - logWorVmax < _logDispersionEpsilon)
                        indexHigh = indexMid;
                    else
                        indexLow = indexMid;
                    indexMid = (int) Math.round(0.5 * (indexLow + indexHigh));
                }
                return indexMid;
            }
        }

        double sumWV(int jkL, int jkU, double logWVMax, double logZ, CalWVdWVd2WV cCal) {
            if (_variancePower < 2) {
                return Math.exp(Math.log(IntStream.rangeClosed(jkL, jkU).mapToDouble(x -> Math.exp(cCal.calculate(x, _alpha,
                        logZ, logWVMax, _variancePower) - logWVMax)).sum()) + logWVMax);
            } else {   // dealing with Vk, not using logWVMax because the sum can be slightly negative...
                double seriesSum = IntStream.rangeClosed(jkL, jkU).mapToDouble(x -> Math.exp(cCal.calculate(x,
                        _alpha, logZ, logWVMax, _variancePower) - logWVMax) * Math.pow(-1, x) * Math.sin(-x * _alphaTimesPI)).sum();

                if (seriesSum > 0)
                    return Math.exp(logWVMax + Math.log(seriesSum));
                else
                    return Math.exp(logWVMax) * seriesSum;
            }
        }

        public int findMaxTermIndex(Chunk[] chks, int rowInd, int colInd) {
            if (chks[RESPIND].atd(rowInd) != 0)
                return (int) Math.max(1, Math.ceil(chks[colInd].atd(rowInd) * _oneOverDispersion));
            else
                return 0;
        }

        public double calLogZ(Chunk[] chks, int rInd, int zConstCol) {
            if (chks[RESPIND].atd(rInd) != 0)
                return Math.log(chks[zConstCol].atd(rInd)) + _alphaMinus1TLogDispersion;
            else
                return 0;
        }

        public double calLogWVMax(Chunk[] chks, int rowInd, int indexMax, double logZ) {
            double resp = chks[RESPIND].atd(rowInd);
            if (_variancePower < 2 && resp != 0) {    //  1 < p < 2
                return indexMax * logZ - logGamma(1 + indexMax) - logGamma(-_alpha * indexMax);
            } else { //p > 2 
                if (resp != 0)
                    return indexMax * logZ + logGamma(1 + _alpha * indexMax) - logGamma(1 + indexMax);
                else
                    return 0;
            }
        }

        public double evalDlldPhi(Chunk[] chks, int rowInd, double sumDWVj, double oneOverSumWVj,
                                  Map<ConstColNames, Integer> constColName2Ind) {
            double response = chks[RESPIND].atd(rowInd);
            if (response == 0)
                return chks[constColName2Ind.get(FirstOrderDerivConst)].atd(rowInd) * _oneOverPhiSquare;
            else if (Double.isFinite(response))
                return chks[constColName2Ind.get(FirstOrderDerivConst)].atd(rowInd) * _oneOverPhiSquare +
                        sumDWVj * oneOverSumWVj;
            else
                return 0.0;
        }

        public double evalD2lldPhi2(Chunk[] chks, int rowInd, double sumDWVj, double sumD2WVj, double oneOverSumWVj,
                                    Map<ConstColNames, Integer> constColName2Ind) {
            double response = chks[RESPIND].atd(rowInd);
            if (response == 0) {
                return chks[constColName2Ind.get(SecondOrderDerivConst)].atd(rowInd) * _oneOverPhi3;
            } else if (Double.isFinite(response)) {
                return chks[constColName2Ind.get(SecondOrderDerivConst)].atd(rowInd) * _oneOverPhi3 +
                        sumD2WVj * oneOverSumWVj - sumDWVj * sumDWVj * oneOverSumWVj * oneOverSumWVj;
            } else {
                return 0.0;
            }
        }

        public double evalLogLikelihood(Chunk[] chks, int rowInd, double sumWV,
                                        Map<ConstColNames, Integer> constColName2Ind) {
            double response = chks[RESPIND].atd(rowInd);
            double logPart2 = _oneOverDispersion * chks[constColName2Ind.get(LogPart2Const)].atd(rowInd);
            if (Double.isFinite(response)) {
                if (response == 0.0) {
                    return logPart2;
                } else {
                    if (_variancePower < 2)
                        return Math.log(sumWV) + chks[constColName2Ind.get(LogOneOverY)].atd(rowInd) + logPart2;
                    else
                        return Math.log(sumWV) + chks[constColName2Ind.get(LogOneOverPiY)].atd(rowInd) + logPart2;
                }
            } else {
                return 0.0;
            }
        }

        /***
         * This interface is used to calculate one item of the series in log.
         */
        public interface CalWVdWVd2WV {
            public double calculate(int jOrk, double alpha, double logZ, double funcMax, double varianceP);
        }

        public static class EvalLogWVEnv implements CalWVdWVd2WV {
            @Override
            public double calculate(int jOrk, double alpha, double logZ, double funcMax, double varianceP) {
                if (varianceP < 2) {
                    return jOrk * logZ - logGamma(1 + jOrk) - logGamma(-alpha * jOrk);
                } else {
                    return jOrk * logZ + logGamma(1 + alpha * jOrk) - logGamma(1 + jOrk);
                }
            }
        }

        public static class EvalLogDWVEnv implements CalWVdWVd2WV {

            @Override
            public double calculate(int jOrk, double alpha, double logZ, double funcMax, double varianceP) {
                return (new EvalLogWVEnv()).calculate(jOrk, alpha, logZ, funcMax, varianceP) + Math.log(jOrk);
            }
        }

        public static class EvalLogD2WVEnv implements CalWVdWVd2WV {

            @Override
            public double calculate(int jOrk, double alpha, double logZ, double funcMax, double varianceP) {
                return (new EvalLogWVEnv()).calculate(jOrk, alpha, logZ, funcMax, varianceP) + 2 * Math.log(jOrk);
            }
        }
    }

    public static class GenPrediction extends MRTask<GenPrediction> {
        final GLMModel _m;
        final DataInfo _dinfo;
        final boolean _sparse;
        private final double[] _beta;

        public GenPrediction(double[] beta, GLMModel m, DataInfo dinfo) {
            _beta = beta;
            _m = m;
            _dinfo = dinfo;
            _sparse = FrameUtils.sparseRatio(dinfo._adaptedFrame) < .5;
        }

        public void map(Chunk[] chks, NewChunk[] preds) {
            double[] ps;
            ps = new double[_m._output._nclasses + 1];
            float[] res = new float[1];
            final int nc = _m._output.nclasses();
            final int ncols = nc == 1 ? 1 : nc + 1; // Regression has 1 predict col; classification also has class distribution
            // compute
            if (_sparse) {
                for (DataInfo.Row r : _dinfo.extractSparseRows(chks))
                    processRow(r, res, ps, preds, ncols);
            } else {
                DataInfo.Row r = _dinfo.newDenseRow();
                for (int rid = 0; rid < chks[0]._len; ++rid) {
                    _dinfo.extractDenseRow(chks, rid, r);
                    processRow(r, res, ps, preds, ncols);
                }
            }
        }

        private void processRow(DataInfo.Row r, float[] res, double[] ps, NewChunk[] preds, int ncols) {
            if (_dinfo._responses != 0) res[0] = (float) r.response[0];
            if (r.predictors_bad) {
                Arrays.fill(ps, Double.NaN);
            } else if (r.weight == 0) {
                Arrays.fill(ps, 0);
            } else {
                ps[0] = _m._parms.linkInv(r.innerProduct(_beta) + r.offset);
            }
            for (int c = 0; c < ncols; c++)  // Output predictions; sized for train only (excludes extra test classes)
                preds[c].addNum(ps[c]);
        }
    }
}
