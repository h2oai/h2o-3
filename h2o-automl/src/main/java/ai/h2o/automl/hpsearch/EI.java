package ai.h2o.automl.hpsearch;

import org.apache.commons.math3.distribution.NormalDistribution;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.TwoDimTable;

/**
 * def calculate_f():
 *             z = (self.eta - m - self.par) / s
 *             return (self.eta - m - self.par) * norm.cdf(z) + s * norm.pdf(z)
 */
public class EI extends AcquisitionFunction {

  private double _incumbent = 0.0;
  private boolean _incumbentColdStartSetupHappened = false;
  public boolean isIncumbentColdStartSetupHappened() {
    return _incumbentColdStartSetupHappened;
  }
  
  // Exploration vs. exploitation tradeOff
  private double _tradeOff = 0.0;
  
  // Note: we assume that we are using EI for optimisation of metrics that are positive.
  private boolean _theBiggerTheBetter;

  public EI(double tradeoff, boolean theBiggerTheBetter) {
    _tradeOff = tradeoff;
    _theBiggerTheBetter = theBiggerTheBetter;
  }

  public void setIncumbent(double incumbent) {
    _incumbent = incumbent;
    _incumbentColdStartSetupHappened = true; 
  }
  
  public void updateIncumbent(double possiblyNewIncumbent) {
    if(possiblyNewIncumbent > _incumbent) setIncumbent(possiblyNewIncumbent);
  }

  /**
   * 
   * @param medians per line predictions of mean marginalised over all predictors 
   * @param variances per line sds of predictions marginalised over all predictors
   * @return weighted(exploitation/extrapolation) evaluation of the unseen entries from the hyperparameters space
   */
  public Vec compute(Vec medians, Vec variances) {
    Vec mediansCopy = medians.makeCopy();
    Vec variancesCopy = variances.makeCopy();
    Frame zComponents = null;
    Frame pdfComponents = null;
    Frame cdfComponents = null;
    try {
      zComponents = new Frame(new String[]{"median", "variance"}, new Vec[]{mediansCopy, variancesCopy});
      Vec zeroVecForM = mediansCopy.makeCon(0);
      zComponents.add("mTerm", zeroVecForM);
      int mTermColumnIdx = 2;

      Vec zeroVec = mediansCopy.makeCon(0);
      String zColumnName = "z_value";
      zComponents.add(zColumnName, zeroVec);
      int zValueColumnIdx = 3;

      new ComputeMTermAndZ(_incumbent, _tradeOff, _theBiggerTheBetter, mTermColumnIdx, zValueColumnIdx).doAll(zComponents);

      Log.debug("MTerm and Z");
      Vec Z = zComponents.vec(zValueColumnIdx);
      Vec mTerm = zComponents.vec(mTermColumnIdx);

      Vec zeroVecForCDF = mediansCopy.makeCon(0);
      cdfComponents = new Frame(new String[]{"z", "cdf"}, new Vec[]{Z, zeroVecForCDF});

      Vec zeroVecForPDF = mediansCopy.makeCon(0);
      pdfComponents = new Frame(new String[]{"z", "pdf"}, new Vec[]{Z, zeroVecForPDF});

      new ComputePDF().doAll(pdfComponents);
      new ComputeCDF().doAll(cdfComponents);

      Vec zeroVecForAF = mediansCopy.makeCon(0);
      String acquisitionFunColumnName = "af";
      Frame afComponents = new Frame(new String[]{"mterm", "variance", "pdf", "cdf", acquisitionFunColumnName}, new Vec[]{mTerm, variancesCopy, pdfComponents.vec("pdf"), cdfComponents.vec("cdf"), zeroVecForAF});

      new ComputeAF().doAll(afComponents);
      
      Vec afComponentsVec = afComponents.vec(acquisitionFunColumnName);
      
      return afComponentsVec;
    } finally {
      if(zComponents!= null) zComponents.delete();
      if(cdfComponents!= null) cdfComponents.delete();
      if(pdfComponents!= null) pdfComponents.delete();
      mediansCopy.remove();
      variancesCopy.remove();
    }
  }

  static class ComputeMTermAndZ extends MRTask<ComputeMTermAndZ> {
    private double _incumbent;
    private double _tradeoff;
    private int _mTermIdx;
    private int _zValueColumnIdx;
    private boolean _theBiggerTheBetter;

    ComputeMTermAndZ(double incumbent, double tradeoff, boolean theBiggerTheBetter, int mTermIdx, int zValueColumnIdx) {
      _incumbent = incumbent;
      _tradeoff = tradeoff;
      _theBiggerTheBetter = theBiggerTheBetter;
      _mTermIdx = mTermIdx;
      _zValueColumnIdx = zValueColumnIdx;
    }

    @Override
    public void map(Chunk cs[]) {
      Chunk median = cs[0];
      Chunk variance = cs[1];
      Chunk mTermColumn = cs[_mTermIdx];
      Chunk zColumn = cs[_zValueColumnIdx];
      for (int i = 0; i < median._len; i++) {
        double mTermValue = _theBiggerTheBetter ? median.atd(i) - _incumbent + _tradeoff : _incumbent - median.atd(i) - _tradeoff;
        mTermColumn.set(i, mTermValue);
        zColumn.set(i, mTermValue / Math.sqrt(variance.atd(i)));
      }
    }
  }

  static class ComputeCDF extends MRTask<ComputeCDF> {

    @Override
    public void map(Chunk cs[]) {
      Chunk z = cs[0];
      Chunk cdf = cs[1];

      for (int i = 0; i < z._len; i++) {
        cdf.set(i, new NormalDistribution().cumulativeProbability(z.atd(i)));
      }
    }
  }
  
  static class ComputePDF extends MRTask<ComputePDF> {

    @Override
    public void map(Chunk cs[]) {
      Chunk z = cs[0];
      Chunk pdf = cs[1];
      for (int i = 0; i < z._len; i++) {
        pdf.set(i, new NormalDistribution().density(z.atd(i)));
      }
    }
  }

  static class ComputeAF extends MRTask<ComputeAF> {

    @Override
    public void map(Chunk[] cs) {
      Chunk mterm = cs[0];
      Chunk variance = cs[1];
      Chunk pdf = cs[2];
      Chunk cdf = cs[3];
      Chunk af = cs[4];
      for (int i = 0; i < mterm._len; i++) {
        double exploitationTerm = mterm.atd(i) * cdf.atd(i);
        double explorationTerm = Math.sqrt(variance.atd(i)) * pdf.atd(i);
        Log.info("Exploitation/exploration ->  " + exploitationTerm + " / " + explorationTerm);
//        Log.warn("!!!!!!!!!!!!!!! exploitationTerm is commented out" );
        af.set(i, exploitationTerm + explorationTerm * 1.3);
      }
    }
  }

  public static void printOutFrameAsTable(Frame fr) {
    printOutFrameAsTable(fr, false, fr.numRows());
  }

  public static void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
    assert limit <= Integer.MAX_VALUE;
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
    System.out.println(twoDimTable.toString(2, true));
  }
}
