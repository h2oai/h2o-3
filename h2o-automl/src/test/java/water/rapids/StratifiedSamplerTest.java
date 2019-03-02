package water.rapids;

import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import static water.rapids.StratifiedSampler.sample;
import static org.junit.Assert.*;

public class StratifiedSamplerTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  @Test
  public void sampleIsWorkingWithProvidedRatio() {
    Frame frameThatWillBeSampledByHalf = null;
    Frame sampledByHalf = null;
    Frame onlySurvivedBeforeSampling = null;
    Frame onlySurvivedFrame = null;
    try {
      frameThatWillBeSampledByHalf = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String responseColumnName = "survived";

      sampledByHalf = StratifiedSampler.sample(frameThatWillBeSampledByHalf, responseColumnName, 0.5, 1234L);
      printOutFrameAsTable(sampledByHalf, true, sampledByHalf.numRows());
      int domainIndexForSurvivedLabel = 1;
      onlySurvivedBeforeSampling = TargetEncoderFrameHelper.filterByValue(frameThatWillBeSampledByHalf, frameThatWillBeSampledByHalf.find(responseColumnName), domainIndexForSurvivedLabel);
      long numberOfSurvivedBeforeSampling = onlySurvivedBeforeSampling.numRows();
      onlySurvivedFrame = TargetEncoderFrameHelper.filterByValue(sampledByHalf, sampledByHalf.find(responseColumnName), domainIndexForSurvivedLabel);
      long numberOfSurvivedAfterSampling = onlySurvivedFrame.numRows();
      assertTrue(numberOfSurvivedAfterSampling <= numberOfSurvivedBeforeSampling * 0.5 + 2 && numberOfSurvivedAfterSampling >= numberOfSurvivedBeforeSampling * 0.5 - 2);
    } finally {
      sampledByHalf.delete();
      onlySurvivedBeforeSampling.delete();
      onlySurvivedFrame.delete();
      frameThatWillBeSampledByHalf.delete();
    }
  }

  @Test
  public void splitIsWorkingWithProvidedRatio() {
    Frame fr = null;
    Frame[] inAndOutFrame = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String responseColumnName = "survived";

      inAndOutFrame = StratifiedSampler.split(fr, responseColumnName, 0.5, 1234L);
      long inNumRows = inAndOutFrame[0].numRows();
      long outNumRows = inAndOutFrame[1].numRows();
      assertTrue(inNumRows + outNumRows == fr.numRows());
      assertFalse(isBitIdentical(inAndOutFrame[0],  inAndOutFrame[1]));

      int indexForSurvived = 1;
      Frame onlySurvivedFrame = TargetEncoderFrameHelper.filterByValue(fr, fr.find(responseColumnName), indexForSurvived);
      Frame inOnlySurvivedFrame = TargetEncoderFrameHelper.filterByValue(inAndOutFrame[0], fr.find(responseColumnName), indexForSurvived);
      Frame outOnlySurvivedFrame = TargetEncoderFrameHelper.filterByValue(inAndOutFrame[1], fr.find(responseColumnName), indexForSurvived);
      assertEquals(onlySurvivedFrame.numRows(), inOnlySurvivedFrame.numRows() + outOnlySurvivedFrame.numRows());
      onlySurvivedFrame.delete();
      inOnlySurvivedFrame.delete();
      outOnlySurvivedFrame.delete();
    } finally {
      fr.delete();
      for(Frame frame :inAndOutFrame) {
        frame.delete();
      }
    }
  }
  
  @Test
  public void underlyingStratifiedSplitDoesNotLeakTest() {
    final String[] STRATA_DOMAIN = new String[]{"in", "out"};
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

    Vec stratifiedSplitVec = StratifiedSplit.split(fr.vec("survived"), 0.5, 1234, STRATA_DOMAIN);
    stratifiedSplitVec.remove();
    fr.delete();
  }

  @Test
  public void stratifiedSampleIsWorkingForNumColumn() {

    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("yes", "yes", "no", "no", "no", "no"))
              .build();

      printOutFrameAsTable(fr);

      Frame filtered = sample(fr,"ColA",0.5, 1234);

      int domainIndexForLabelYes = 0;
      Frame onlyYesFrame = TargetEncoderFrameHelper.filterByValue(filtered, 0, domainIndexForLabelYes);
      Assert.assertTrue(onlyYesFrame.numRows() == 1);
      printOutFrameAsTable(filtered);
      filtered.delete();
      onlyYesFrame.delete();
    }
    finally {
      Scope.exit();
    }
  }

}
