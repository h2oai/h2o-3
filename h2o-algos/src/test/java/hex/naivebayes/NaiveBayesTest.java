package hex.naivebayes;

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

      try {
        job = new NaiveBayes(parms);
        model = job.trainModel().get();
        score = model.score(train);
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

  @Test public void testCovtype() throws InterruptedException, ExecutionException {
    NaiveBayes job = null;
    NaiveBayesModel model = null;
    Frame train = null, score = null;

    try {
      // Scope.enter();
      train = parse_test_file(Key.make("covtype.hex"), "smalldata/covtype/covtype.20k.enum.data");
      // Scope.track(train.replace(54, train.vecs()[54].toEnum())._key);
      // DKV.put(train);

      NaiveBayesParameters parms = new NaiveBayesParameters();
      parms._train = train._key;
      parms._laplace = 0;
      parms._response_column = "V55";
      try {
        job = new NaiveBayes(parms);
        model = job.trainModel().get();
        score = model.score(train);
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
      // Scope.exit();
    }
  }
}
