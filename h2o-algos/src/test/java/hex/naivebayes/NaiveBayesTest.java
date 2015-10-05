package hex.naivebayes;

import hex.SplitFrame;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import hex.naivebayes.NaiveBayesModel.NaiveBayesParameters;

import java.util.concurrent.ExecutionException;

public class NaiveBayesTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testIris() throws InterruptedException, ExecutionException {
    NaiveBayes job = null;
    NaiveBayesModel model = null;
    Frame train = null, score = null;
    try {
      train = parse_test_file(Key.make("iris_wheader.hex"), "smalldata/iris/iris_wheader.csv");
      NaiveBayesParameters parms = new NaiveBayesParameters();
      parms._train = train._key;
      parms._laplace = 0;
      parms._response_column = train._names[4];
      parms._compute_metrics = false;

      try {
        job = new NaiveBayes(parms);
        model = job.trainModel().get();

        // Done building model; produce a score column with class assignments
        score = model.score(train);
        Assert.assertTrue(model.testJavaScoring(train,score,1e-6));
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        if (job != null) job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) model.delete();
    }
  }

  @Test public void testIrisValidation() throws InterruptedException, ExecutionException {
    NaiveBayes job = null;
    NaiveBayesModel model = null;
    Frame fr = null, fr2 = null;
    Frame tr = null, te  = null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      SplitFrame sf = new SplitFrame();
      sf.dataset = fr;
      sf.ratios = new double[] { 0.5, 0.5 };
      sf.destination_frames = new Key[] { Key.make("train.hex"), Key.make("test.hex") };

      // Invoke the job
      sf.exec().get();
      Key[] ksplits = sf.destination_frames;
      tr = DKV.get(ksplits[0]).get();
      te = DKV.get(ksplits[1]).get();

      NaiveBayesParameters parms = new NaiveBayesParameters();
      parms._train = ksplits[0];
      parms._valid = ksplits[1];
      parms._laplace = 0.01;    // Need Laplace smoothing
      parms._response_column = fr._names[4];
      parms._compute_metrics = true;

      try {
        job = new NaiveBayes(parms);
        model = job.trainModel().get();

        // Done building model; produce a score column with class assignments
        fr2 = model.score(te);
        Assert.assertTrue(model.testJavaScoring(te,fr2,1e-6));
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        if (job != null) job.remove();
      }
    } finally {
      if( fr  != null ) fr.delete();
      if( fr2 != null ) fr2.delete();
      if( tr  != null ) tr .delete();
      if( te  != null ) te .delete();
      if( model != null ) model.delete();
    }
  }

  @Test public void testProstate() throws InterruptedException, ExecutionException {
    NaiveBayes job = null;
    NaiveBayesModel model = null;
    Frame train = null, score = null;
    final int[] cats = new int[]{1,3,4,5};    // Categoricals: CAPSULE, RACE, DPROS, DCAPS

    try {
      Scope.enter();
      train = parse_test_file(Key.make("prostate.hex"), "smalldata/logreg/prostate.csv");
      for(int i = 0; i < cats.length; i++)
        Scope.track(train.replace(cats[i], train.vec(cats[i]).toCategoricalVec())._key);
      train.remove("ID").remove();
      DKV.put(train._key, train);

      NaiveBayesParameters parms = new NaiveBayesParameters();
      parms._train = train._key;
      parms._laplace = 0;
      parms._response_column = train._names[0];
      parms._compute_metrics = true;

      try {
        job = new NaiveBayes(parms);
        model = job.trainModel().get();

        // Done building model; produce a score column with class assignments
        score = model.score(train);
        Assert.assertTrue(model.testJavaScoring(train,score,1e-6));
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        if (job != null) job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) model.delete();
      Scope.exit();
    }
  }

  @Test public void testCovtype() throws InterruptedException, ExecutionException {
    NaiveBayes job = null;
    NaiveBayesModel model = null;
    Frame train = null, score = null;

    try {
      Scope.enter();
      train = parse_test_file(Key.make("covtype.hex"), "smalldata/covtype/covtype.20k.data");
      Scope.track(train.replace(54, train.vecs()[54].toCategoricalVec())._key);   // Change response to categorical
      DKV.put(train);

      NaiveBayesParameters parms = new NaiveBayesParameters();
      parms._train = train._key;
      parms._laplace = 0;
      parms._response_column = train._names[54];
      parms._compute_metrics = false;

      try {
        job = new NaiveBayes(parms);
        model = job.trainModel().get();

        // Done building model; produce a score column with class assignments
        score = model.score(train);
        Assert.assertTrue(model.testJavaScoring(train,score,1e-6));
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        if (job != null) job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) model.delete();
      Scope.exit();
    }
  }
}
