package hex.glm;

import hex.DataInfo;
import water.Job;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.util.ArrayUtils;

import java.util.Arrays;

import static hex.glm.GLMUtils.removeRedCols;
import static hex.util.LinearAlgebraUtils.matrixMultiply;
import static water.util.ArrayUtils.*;

/***
 * Classes defined here implemented the various pieces of regression influence diagnostics described in this doc:
 * https://github.com/h2oai/h2o-3/issues/7044.  Hence, whenever I refer to the document, I mean the one in the
 * http link.
 */
public class RegressionInfluenceDiagnosticsTasks {
  public static class RegressionInfluenceDiagBinomial extends MRTask<RegressionInfluenceDiagBinomial> {
    final double[] _beta;
    final double[][] _gramInv;  // could be with standardized or non-standardized predictors, not scaled with obj_reg
    final Job _j;
    final int _betaSize;
    final int _reducedBetaSize;
    final GLMModel.GLMParameters _parms;
    final DataInfo _dinfo;
    final double[] _stdErr;
    final boolean _foundRedCols;
    final double[] _oneOverStdErr;

    public RegressionInfluenceDiagBinomial(Job j, double[] beta, double[][] gramInv, GLMModel.GLMParameters parms, 
                                           DataInfo dinfo, double[] stdErr) {
      _j = j;
      _beta = beta; // denormalized beta
      _betaSize = beta.length;
      _reducedBetaSize = gramInv.length;
      _foundRedCols = !(_betaSize == _reducedBetaSize);
      _gramInv = gramInv; // not scaled by parms._obj_reg
      _parms = parms;
      _dinfo = dinfo;
      _stdErr = stdErr;
      _oneOverStdErr = Arrays.stream(_stdErr).map(x -> 1.0/x).toArray();
      
    }

    @Override
    public void map(Chunk[] chks, NewChunk[] nc) {
      if (isCancelled() || _j != null && _j.stop_requested()) return;
      double[] dfbetas = new double[_betaSize];
      double[] dfbetasReduced = new double[_reducedBetaSize];
      double[] row2Array = new double[_betaSize];
      double[] row2ArrayReduced = new double[_reducedBetaSize];
      double[] xTimesGramInv = new double[_reducedBetaSize];
      DataInfo.Row r = _dinfo.newDenseRow();
      for (int rid = 0; rid < chks[0]._len; ++rid) {
        _dinfo.extractDenseRow(chks, rid, r);
        genDfBetasRow(r, nc, row2Array, row2ArrayReduced, dfbetas, dfbetasReduced, xTimesGramInv);
      }

      if (_j != null)
       _j.update(1);
    }

    private void genDfBetasRow(DataInfo.Row r, NewChunk[] nc, double[] row2Array, double[] row2ArrayRed, 
                               double[] dfbetas, double[] dfbetasRed, double[] xTimesGramInv) {
      if (r.response_bad) {
        Arrays.fill(dfbetas, Double.NaN);
      } else if (r.weight == 0) {
        Arrays.fill(dfbetas, 0.0);
      } else {
        r.expandCatsPredsOnly(row2Array);  // change Row to array
        if (_foundRedCols) {
          removeRedCols(row2Array, row2ArrayRed, _stdErr);
          genDfBeta(r, row2ArrayRed, xTimesGramInv, dfbetasRed, nc);
        } else {
          genDfBeta(r, row2Array, xTimesGramInv, dfbetas, nc);
        }
      }
    }
    
    private void genDfBeta(DataInfo.Row r, double[] row2Array, double[] xTimesGramInv, double[] dfbetas, NewChunk[] nc) {
      double mu = _parms.linkInv(r.innerProduct(_beta)+r.offset); // generate p hat
      // generate residual
      double residual = r.response(0)-mu;
      double oneOverMLL = gen1OverMLL(row2Array, xTimesGramInv, mu, r.weight);  // 1.0/(oneOverObjReg-hjj)
      genDfBetas(oneOverMLL, residual, row2Array, dfbetas, r.weight);

      for (int c = 0; c < _reducedBetaSize; c++) // copy dfbetas over to new chunks
        nc[c].addNum(dfbetas[c]);
    }

    /***
     * implement operations on and in between equation 5, 6 of the document
     */
    public void genDfBetas(double oneOverMLL, double residual, double[] row2Array, double[] dfbetas, double weight) {
      double resOverMLL = oneOverMLL*residual*weight;
      int count=0;
      for (int index=0; index<_betaSize; index++) {
        if (!Double.isNaN(_stdErr[index])) {
          dfbetas[count] = resOverMLL * _oneOverStdErr[index] * ArrayUtils.innerProduct(row2Array, _gramInv[count]);
          count++;
        }
      }
    }

    /***
     * Generate 1.0/(1.0-hjj) for each data row j.  Implement equation 8 of the document for binomial family.
     */
    public double gen1OverMLL(double[] row2Array, double[] xTimesGramInv, double mu, double weight) {
      for (int index = 0; index< _reducedBetaSize; index++) {  // form X*invGram
        xTimesGramInv[index] = ArrayUtils.innerProduct(row2Array, _gramInv[index]);
      }
      double hjj = weight*mu*(1-mu)*ArrayUtils.innerProduct(xTimesGramInv, row2Array);
      return 1.0/(1.0-hjj);
    }
  }

  /***
   * generate DFBETAS as in equation 4 of the document.
   */
  public static class RegressionInfluenceDiagGaussian extends MRTask<RegressionInfluenceDiagGaussian> {
    final double[] _oneOverSqrtXTXDiag;
    final double[] _betas;  // Exclude redundant columns if present
    final int _betaSize;
    final Job _j;
    
    public RegressionInfluenceDiagGaussian(double[][] xTx, double[] betas, Job j) {
      _betas = betas;
      _betaSize = betas.length;
      _j = j;
      _oneOverSqrtXTXDiag = new double[_betaSize];
      for (int index = 0; index< _betaSize; index++)
        _oneOverSqrtXTXDiag[index] = 1.0/Math.sqrt(xTx[index][index]);
    }
    
    @Override
    public void map(Chunk[] chks, NewChunk[] ncs) {
      if (isCancelled() || (_j != null && _j.stop_requested()))
        return;
      double[] betaDiff = new double[_betaSize];
      int numCols = chks.length;
      double[] row2Array = new double[numCols]; // contains new beta and var estimate of ith row
      int len = chks[0]._len;
      for (int index=0; index<len; index++) {
        readRow2Array(row2Array, chks, index, numCols);
        setBetaDiff(betaDiff, row2Array, ncs);
      }
    }
    
    private void setBetaDiff(double[] betaDiff, double[] row2Array, NewChunk[] nc) {
      if (!Double.isFinite(row2Array[0])) {
        Arrays.fill(betaDiff, Double.NaN);
      } else {
        double oneOverVarEst = 1.0 / Math.sqrt(row2Array[_betaSize]);
        for (int index = 0; index < _betaSize; index++)
          betaDiff[index] = (_betas[index] - row2Array[index]) * oneOverVarEst * _oneOverSqrtXTXDiag[index];
      }
      for (int colIndex = 0; colIndex< _betaSize; colIndex++)  // write new beta to new chunk
        nc[colIndex].addNum(betaDiff[colIndex]);
    }
    
    private void readRow2Array(double[] row2Array, Chunk[] chks, int rInd, int nCol) {
      for (int index=0; index<nCol; index++)
        row2Array[index] = chks[index].atd(rInd);
    }
  }
  
  public static class ComputeNewBetaVarEstimatedGaussian extends MRTask<ComputeNewBetaVarEstimatedGaussian> {
    final double[][] _cholInv;  // XTX inverse: store cholInv without redundant predictors, not scaled by parms._obj_reg
    final double[] _xTransY;    // store XTY of full dataset
    final double[] _xTransYReduced; // same as xTransY, but changed when there is redundant columns
    final int _betaSize;
    final int _reducedBetaSize;
    final int _newChunkWidth;
    final Job _j;
    final DataInfo _dinfo;
    final double[][] _xTx;  // not scaled by parms._obj_reg
    final double _weightedNobs;
    final double _sumRespSq;
    final boolean _foundRedCols;
    final double[] _stdErr; // used to tell which predict is redundant
    
    public ComputeNewBetaVarEstimatedGaussian(double[][] cholInv, double[] xTY, Job j, DataInfo dinfo, double[][] gram,
                                              double nobs, double sumRespSq, double[] stdErr) {
      _cholInv = cholInv;
      _xTransYReduced = xTY; // if redundant columns present, is reduced
      _betaSize = stdErr.length;
      _reducedBetaSize = cholInv.length;
      _foundRedCols = !(_betaSize == _reducedBetaSize);
      _newChunkWidth = _betaSize+1; // last one is for estimated variance
      _j = j;
      _dinfo = dinfo;
      _xTx = gram;
      _weightedNobs = nobs-_reducedBetaSize; // intercept already included in gram/chol
      _sumRespSq = sumRespSq; // YTY
      _stdErr = stdErr;
      _xTransY = new double[_betaSize];
      if (_foundRedCols) { // shrink xTransY
       int count=0;
       for (int index=0; index<_betaSize; index++)
         if (!Double.isNaN(stdErr[index]))
           _xTransY[index] = _xTransYReduced[count++];
      } else {
        System.arraycopy(_xTransYReduced, 0, _xTransY, 0, _reducedBetaSize);
      }
    }

    @Override
    public void map(Chunk[] chks, NewChunk[] nc) {
      if (isCancelled() || (_j != null && _j.stop_requested()))
        return; // timeout
      double[] newBeta = new double[_betaSize];
      double[] newBetaRed = new double[_reducedBetaSize];
      double[] row2Array = new double[_betaSize];
      double[] row2ArrayRed = new double[_reducedBetaSize];
      double[][] tmpDoubleArray = new double[_reducedBetaSize][_reducedBetaSize];
      double[] tmpArray = new double[_betaSize];
      double[] tmpArrayRed = new double[_reducedBetaSize];
      final int chkLen = chks[0]._len;
      DataInfo.Row r = _dinfo.newDenseRow();
      for (int rowIndex=0; rowIndex<chkLen; rowIndex++) {
        _dinfo.extractDenseRow(chks, rowIndex, r);
        getNewBetaVarEstimate(r, nc, row2Array, row2ArrayRed, newBeta, newBetaRed, tmpArray, tmpArrayRed, tmpDoubleArray);
      }
      
      if (_j != null)
        _j.update(1);
    }
    
    private void getNewBetaVarEstimate(DataInfo.Row r, NewChunk[] newBetasChunk, double[] row2Array, 
                                       double[] row2ArrayRed, double[] newBetas, double[] newBetaRed, double[] tmpArray,
                                       double[] tmpArrayRed, double[][] xiTransxi) {
      double varEstimate;
      if (r.response_bad) {
        varEstimate = Double.NaN;
        if (_foundRedCols) {
          Arrays.fill(newBetaRed, Double.NaN);
          writeNewChunk(newBetaRed, newBetasChunk, varEstimate);
        } else {
          Arrays.fill(newBetas, Double.NaN);
          writeNewChunk(newBetas, newBetasChunk, varEstimate);
        }
      } else if (r.weight == 0.0) {
        varEstimate = 0.0;
        if (_foundRedCols) {
          Arrays.fill(newBetaRed, 0.0);
          writeNewChunk(newBetaRed, newBetasChunk, varEstimate);
        } else {
          Arrays.fill(newBetas, 0.0);
          writeNewChunk(newBetas, newBetasChunk, varEstimate);
        }
      } else {
        r.expandCatsPredsOnly(row2Array);   // contains redundant columns if present
        if (_foundRedCols) {  // form xi*trans(xi) with only non-redundant columns
          removeRedCols(row2Array, row2ArrayRed, _stdErr);
          ArrayUtils.outerProduct(xiTransxi, row2ArrayRed, row2ArrayRed);
        } else {
          ArrayUtils.outerProduct(xiTransxi, row2Array, row2Array);
        }
        double[][] cholInvTimesOuterProduct = matrixMultiply(_cholInv, xiTransxi); // form inv XTX*trans(xi)*xi
        double[][] cholInvOuterCholInv = matrixMultiply(cholInvTimesOuterProduct, _cholInv); // form inv(XTX)*xi*trans(xi)*inv(XTX)
        if (_foundRedCols) {
          genNewBetas(row2ArrayRed, tmpArrayRed, newBetaRed, r, cholInvOuterCholInv);
          fillBetaRed2Full(newBetaRed, newBetas);
          varEstimate = genVarEstimate(r, tmpArrayRed, newBetaRed, newBetas);
          writeNewChunk(newBetaRed, newBetasChunk, varEstimate);
        } else {
          genNewBetas(row2Array, tmpArray, newBetas, r, cholInvOuterCholInv);
          varEstimate = genVarEstimate(r, tmpArray, newBetas, newBetas);
          writeNewChunk(newBetas, newBetasChunk, varEstimate);
        }
      }
    }
    
    private void fillBetaRed2Full(double[] newBetaRed, double[] newBetas) {
      int count=0;
      for (int index=0; index<_betaSize; index++) {
        if (Double.isNaN(_stdErr[index])) {
          newBetas[index] = 0.0;
        } else {
          newBetas[index] = newBetaRed[count++];
        }
      }
    }

    /***
     * calculate beta without current row r as in equation 8 of the document.
     */
    private void genNewBetas(double[] row2Array, double[] tmpArray, double[] newBetas, DataInfo.Row r,
                               double[][] cholInvOuterCholInv) {
      multArrVec(_cholInv, row2Array, tmpArray); // inv(gram)*xiTrans(xi)
      double oneOverdenom = 1.0/(1-innerProduct(row2Array, tmpArray)); // 1.0/(1-tran(xi)*inv(gram)*xi
      mult(cholInvOuterCholInv, oneOverdenom);
      add(cholInvOuterCholInv, _cholInv);
      tmpArray = mult(row2Array, -r.response(0));  // xi*response
      add(tmpArray, _xTransYReduced); // -xi*response + xTransY
      multArrVec(cholInvOuterCholInv, tmpArray, newBetas);
    }
    
    private void writeNewChunk(double[] newBetas, NewChunk[] newBetasChunk, double varEstimate) {
      for (int colIndex=0; colIndex<_reducedBetaSize; colIndex++)  // write new beta to new chunk
        newBetasChunk[colIndex].addNum(newBetas[colIndex]);
      newBetasChunk[_reducedBetaSize].addNum(varEstimate);
    }

    /***
     * Generate the variance estimate as in equation 11 of the document.
     */
    private double genVarEstimate(DataInfo.Row r, double[] tmpArray, double[] newBetasRed, double[] newBetas) {
      double temp = r.response(0)-r.innerProduct(newBetas); // r contains redundant columns here
      double ithVarEst = r.weight*temp*temp;
      multArrVec(_xTx, newBetasRed, tmpArray);
      return (_sumRespSq-2*ArrayUtils.innerProduct(newBetasRed, _xTransYReduced)+
              ArrayUtils.innerProduct(newBetasRed, tmpArray)-ithVarEst)/(_weightedNobs-r.weight);
    }
  }
}
