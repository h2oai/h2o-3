package ai.h2o.automl.targetencoding;

import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.MultinomialModelPrediction;
import hex.naivebayes.NaiveBayes;
import hex.naivebayes.NaiveBayesModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.Key;
import water.NaiveBayes_model_1559919368810_1;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Random;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.register;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class NaiveBayesTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  // This is just a helper method that generates POJO. We can than use this class by putting it into the project.
  // Run another test -> this.predictionsFromGeneratedClass()
  // Notes: TO train model with `42` category having more chances to have 0 or 1 as a response. 
  @Ignore
  @Test public void generateImbalancedDatasetAndTrainNB() throws IOException {
    NaiveBayesModel model = null;
    Scope.enter();
    long seed = new Random().nextLong();
    try {
      int size = 10000000;
      int sizePerChunk = size / 5;
      String[] arr = new String[size];
      for (int a = 0; a < size; a++) {
        int categoricalIntValue = new Random().nextInt(17576);
        arr[a] = Integer.toString(categoricalIntValue);
      }
      String responseColumnName = "y";
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", responseColumnName)
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, arr)
              .withRandomIntDataForCol(1, size, 0, 24, seed)
              .withChunkLayout(sizePerChunk, sizePerChunk, sizePerChunk, sizePerChunk, sizePerChunk)
              .build();

      asFactor(fr, "ColA");
      asFactor(fr, responseColumnName);

      String[] arr2 = new String[20000];

      for (int a = 0; a < 20000; a++) {
        arr2[a] = Integer.toString(42);
      }

      Frame fr2 = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA", responseColumnName)
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, arr2)
              .withRandomIntDataForCol(1, 20000, 5, 7, seed)
              .build();

      asFactor(fr2, "ColA");
      asFactor(fr2, responseColumnName);

      Frame union = rBind(fr, fr2);

      NaiveBayesModel.NaiveBayesParameters parms = new NaiveBayesModel.NaiveBayesParameters();
      parms._train = union._key;
      parms._laplace = 0;
      parms._response_column = responseColumnName;
      parms._compute_metrics = false;
      parms._min_prob = 1e-10;

      model = new NaiveBayes(parms).trainModel().get();

      FileOutputStream modelOutput = new FileOutputStream("bayesian_synthetic.java");
      model.toJava(modelOutput, false, true);

    } finally {
      if (model != null) model.delete();
    }
  }

  Frame rBind(Frame a, Frame b) {
    if(a == null) {
      assert b != null;
      return b;
    } else {
      String tree = String.format("(rbind %s %s)", a._key, b._key);
      return execRapidsAndGetFrame(tree);
    }
  }
  private Frame execRapidsAndGetFrame(String astTree) {
    Val val = Rapids.exec(astTree);
    return register(val.getFrame());
  }
  
  @Test public void predictionsFromGeneratedClass() throws PredictException {
    NaiveBayesModel model = null;
    Scope.enter();
    try {

      EasyPredictModelWrapper trigrammodel;
      trigrammodel = new EasyPredictModelWrapper((hex.genmodel.GenModel) new NaiveBayes_model_1559919368810_1());

      MultinomialModelPrediction p;
      RowData row;

      row = new RowData();

      row.put("ColA", "42");
      p = trigrammodel.predictMultinomial(row);

      double checksum = 0;
      for (int j = 0; j < p.classProbabilities.length; j++) {
        checksum += +p.classProbabilities[j];
        System.out.println(trigrammodel.getResponseDomainValues()[j]+":"+p.classProbabilities[j]);
      }
      
      System.out.println("Total of probabilities:" + checksum);

      // Try for another test point
      MultinomialModelPrediction p2;
      RowData row2 = new RowData();

      row2.put("ColA", "456");
      p2 = trigrammodel.predictMultinomial(row2);

      for (int j = 0; j < p2.classProbabilities.length; j++) {
        System.out.println(trigrammodel.getResponseDomainValues()[j]+":"+p2.classProbabilities[j]);
      }
      
      try {
        assertArrayEquals(p.classProbabilities, p2.classProbabilities, 1e-5);
        Assert.fail("Unexpected that probabilities are equal");
      } catch (Exception ex) {
        if(ex.getMessage().contains("Unexpected") ) throw ex;
        // This is happening because only prior probabilities are being used. No conditional terms are being added during scoring.
      }
      
    } finally {
      if (model != null) model.delete();
    }
  }

}
