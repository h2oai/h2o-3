package hex.naivebayes;

import hex.SplitFrame;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import hex.naivebayes.NaiveBayesModel.NaiveBayesParameters;
import water.fvec.Vec;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

public class NaiveBayesTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testIris() throws InterruptedException, ExecutionException {
    NaiveBayesModel model = null;
    Frame train = null, score = null;
    try {
      train = parseTestFile(Key.make("iris_wheader.hex"), "smalldata/iris/iris_wheader.csv");
      NaiveBayesParameters parms = new NaiveBayesParameters();
      parms._train = train._key;
      parms._laplace = 0;
      parms._response_column = train._names[4];
      parms._compute_metrics = false;

      model = new NaiveBayes(parms).trainModel().get();
      // Done building model; produce a score column with class assignments
      score = model.score(train);
      Assert.assertTrue(model.testJavaScoring(train,score,1e-6));
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) model.delete();
    }
  }

  @Test public void testIrisValidation() throws InterruptedException, ExecutionException {
    NaiveBayesModel model = null;
    Frame fr = null, fr2 = null;
    Frame tr = null, te  = null;
    try {
      fr = parseTestFile("smalldata/iris/iris_wheader.csv");

      SplitFrame sf = new SplitFrame(fr,new double[] { 0.5, 0.5 },new Key[] { Key.make("train.hex"), Key.make("test.hex") });

      // Invoke the job
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      tr = DKV.get(ksplits[0]).get();
      te = DKV.get(ksplits[1]).get();

      NaiveBayesParameters parms = new NaiveBayesParameters();
      parms._train = ksplits[0];
      parms._valid = ksplits[1];
      parms._laplace = 0.01;    // Need Laplace smoothing
      parms._response_column = fr._names[4];
      parms._compute_metrics = true;

      model = new NaiveBayes(parms).trainModel().get();

      // Done building model; produce a score column with class assignments
      fr2 = model.score(te);
      Assert.assertTrue(model.testJavaScoring(te,fr2,1e-6));
    } finally {
      if( fr  != null ) fr.delete();
      if( fr2 != null ) fr2.delete();
      if( tr  != null ) tr .delete();
      if( te  != null ) te .delete();
      if( model != null ) model.delete();
    }
  }

  @Test public void testProstate() throws InterruptedException, ExecutionException {
    NaiveBayesModel model = null;
    Frame train = null, score = null;
    final int[] cats = new int[]{1,3,4,5};    // Categoricals: CAPSULE, RACE, DPROS, DCAPS

    try {
      Scope.enter();
      train = parseTestFile(Key.make("prostate.hex"), "smalldata/logreg/prostate.csv");
      for(int i = 0; i < cats.length; i++)
        Scope.track(train.replace(cats[i], train.vec(cats[i]).toCategoricalVec()));
      train.remove("ID").remove();
      DKV.put(train._key, train);

      NaiveBayesParameters parms = new NaiveBayesParameters();
      parms._train = train._key;
      parms._laplace = 0;
      parms._response_column = train._names[0];
      parms._compute_metrics = true;

      model = new NaiveBayes(parms).trainModel().get();
        
      // Done building model; produce a score column with class assignments
      score = model.score(train);
      Assert.assertTrue(model.testJavaScoring(train,score,1e-6));
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) model.delete();
      Scope.exit();
    }
  }

  @Test public void testCovtype() throws InterruptedException, ExecutionException {
    NaiveBayesModel model = null;
    Frame train = null, score = null;

    try {
      Scope.enter();
      train = parseTestFile(Key.make("covtype.hex"), "smalldata/covtype/covtype.20k.data");
      Scope.track(train.replace(54, train.vecs()[54].toCategoricalVec()));   // Change response to categorical
      DKV.put(train);

      NaiveBayesParameters parms = new NaiveBayesParameters();
      parms._train = train._key;
      parms._laplace = 0;
      parms._response_column = train._names[54];
      parms._compute_metrics = false;

      model = new NaiveBayes(parms).trainModel().get();

      // Done building model; produce a score column with class assignments
      score = model.score(train);
      Assert.assertTrue(model.testJavaScoring(train,score,1e-6));
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) model.delete();
      Scope.exit();
    }
  }

  @Test
  public void testIsFeatureUsedInPredict() {
    isFeatureUsedInPredictHelper(false, false);
    isFeatureUsedInPredictHelper(true, false);
    isFeatureUsedInPredictHelper(false, true);
    isFeatureUsedInPredictHelper(true, true);
  }

  private void isFeatureUsedInPredictHelper(boolean ignoreConstCols, boolean multinomial) {
    Scope.enter();
    Vec target = Vec.makeRepSeq(100, 3);
    if (multinomial) target = target.toCategoricalVec();
    Vec zeros = Vec.makeCon(0d, 100);
    Vec nonzeros = Vec.makeCon(1e10, 100);
    Frame dummyFrame = new Frame(
            new String[]{"a", "b", "c", "d", "e", "target"},
            new Vec[]{zeros, zeros, zeros, zeros, target, target.toCategoricalVec()}
    );
    dummyFrame._key = Key.make("DummyFrame_testIsFeatureUsedInPredict");

    Frame otherFrame = new Frame(
            new String[]{"a", "b", "c", "d", "e", "target"},
            new Vec[]{nonzeros, nonzeros, nonzeros, nonzeros, target, target.toCategoricalVec()}
    );

    Frame reference = null;
    Frame prediction = null;
    NaiveBayesModel model = null;
    try {
      DKV.put(dummyFrame);
      NaiveBayesModel.NaiveBayesParameters nb = new NaiveBayesModel.NaiveBayesParameters();
      nb._train = dummyFrame._key;
      nb._response_column = "target";
      nb._seed = 1;
      nb._ignore_const_cols = ignoreConstCols;

      NaiveBayes job = new NaiveBayes(nb);
      model = job.trainModel().get();

      String lastUsedFeature = "";
      int usedFeatures = 0;
      for(String feature : model._output._names) {
        if (model.isFeatureUsedInPredict(feature)) {
          usedFeatures ++;
          lastUsedFeature = feature;
        }
      }
      assertEquals(1, usedFeatures);
      assertEquals("e", lastUsedFeature);

      reference = model.score(dummyFrame);
      prediction = model.score(otherFrame);
      for (int i = 0; i < reference.numRows(); i++) {
        assertEquals(reference.vec(0).at(i), prediction.vec(0).at(i), 1e-10);
      }
    } finally {
      dummyFrame.delete();
      if (model != null) model.delete();
      if (reference != null) reference.delete();
      if (prediction != null) prediction.delete();
      target.remove();
      zeros.remove();
      nonzeros.remove();
      Scope.exit();
    }
  }
}
