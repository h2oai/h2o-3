package ai.h2o.automl.hpsearch;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import static org.junit.Assert.*;

public class EITest extends TestUtil {

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void compute() {

    EI ei = new EI(0.0, true);
    ei.setIncumbent(5.1);

    Vec medians = Vec.makeVec(ard(0.5, 1.3, 0.3, 1.7, -0.5, 0.5), Vec.newKey());
    Vec variances = Vec.makeVec(ard(0.2, 0.3, 0.1, 1.7, 3, 0.5), Vec.newKey());

    Vec afs = ei.compute(medians, variances);

    Frame frame = new Frame(afs);
    printOutFrameAsTable(frame, false, frame.numRows());
    
  }
  
  @Test
  public void computeCDF() {

    Vec z = Vec.makeVec(ard(0.5, 1.3, 0, 1.7, -0.5, 0.5), Vec.newKey());

    Vec zeroVecForCDF = z.makeCon(0);
    Frame cdfComponents = new Frame(new String[]{"z", "cdf"}, new Vec[]{z, zeroVecForCDF});

    new EI.ComputeCDF().doAll(cdfComponents);

    printOutFrameAsTable(cdfComponents, false, cdfComponents.numRows());
    assertEquals(0.5, cdfComponents.vec("cdf").at(2), 1e-5);
    
  }

  @Test
  public void computePDF() {

    Vec z = Vec.makeVec(ard(0.5, 1.3, 0, 1.7, -0.5, 0.5), Vec.newKey());

    Vec zeroVecForPDF = z.makeCon(0);
    Frame pdfComponents = new Frame(new String[]{"z", "pdf"}, new Vec[]{z, zeroVecForPDF});

    new EI.ComputePDF().doAll(pdfComponents);

    printOutFrameAsTable(pdfComponents, false, pdfComponents.numRows());
    assertEquals( pdfComponents.vec("pdf").at(4),pdfComponents.vec("pdf").at(5), 1e-5);

  }
  
  @Test
  public void computeMTermAndZ() {

    Vec medians = Vec.makeVec(ard(0.5, 1.3, 0.3, 1.7, -0.5, 0.5), Vec.newKey());
    Vec variances = Vec.makeVec(ard(0.2, 0.3, 0.1, 1.7, 3, 0.5), Vec.newKey());

    Frame zComponents = new Frame(new String[]{"median", "variance"}, new Vec[]{medians, variances});

    Vec zeroVecForM = medians.makeCon(0);
    zComponents.add("mTerm", zeroVecForM);
    
    Vec zeroVec = medians.makeCon(0);
    String zColumnName = "z_value";
    zComponents.add(zColumnName, zeroVec);

    new EI.ComputeMTermAndZ(5.1, 0.0, true,2, 3).doAll(zComponents);

    printOutFrameAsTable(zComponents, false, zComponents.numRows());
//    assertEquals( pdfComponents.vec("pdf").at(4),pdfComponents.vec("pdf").at(5), 1e-5);

  }
  
  @Ignore
  @Test
  public void withRealSample() {

    NormalDistribution normalDistribution = new NormalDistribution(5, 1);
    normalDistribution.reseedRandomGenerator(1234);
    
    int sampleSize = 5;
    double[] sample = normalDistribution.sample(sampleSize);

    double mean = normalDistribution.getMean();
    
    double[] variances = new double[sampleSize];
    for (int i = 0; i < sampleSize; i++) {
      
      double variance = Math.pow(sample[i] - mean, 2) / sampleSize ;
      variances[i] = variance;
    }

    EI ei = new EI(0.0, true);
    ei.setIncumbent(5.1);

    Vec medians = Vec.makeVec(sample, Vec.newKey());
    printOutFrameAsTable(new Frame(medians), false, sampleSize);
    
    Vec variancesVec = Vec.makeVec(variances, Vec.newKey());
    printOutFrameAsTable(new Frame(variancesVec), false, sampleSize);

    Vec afs = ei.compute(medians, variancesVec);

    Frame frame = new Frame(afs);
    printOutFrameAsTable(frame, false, frame.numRows());
    int t = 42;
  }
}
