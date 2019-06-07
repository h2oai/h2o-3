package ai.h2o.automl.targetencoding;

import hex.naivebayes.NaiveBayes;
import hex.naivebayes.NaiveBayesModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertTrue;

public class NaiveBayesStressBenchmark extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  // Case: one enum column `ColA` .   This guy will fail from time to time - upt to three time from 50 ( regardless of whether we ensure multiple chunks or not)
  @Test public void testSyntheticWithJavaScoring() throws IOException {
    NaiveBayesModel model = null;
    Scope.enter();
    long seed = new Random().nextLong();
    try {
      int size = 100000;
      int sizePerChunk = size / 5;
      String[] arr = new String[size];
      for (int a = 0; a < size; a++) {
        arr[a] = Integer.toString(new Random().nextInt(100));
      }
      String responseColumnName = "y";
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", responseColumnName)
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, arr)
              .withRandomIntDataForCol(1, size, 0, 100, seed)
//              .withChunkLayout(sizePerChunk, sizePerChunk, sizePerChunk, sizePerChunk, sizePerChunk)
              .build();

      asFactor(fr, "ColA");
      asFactor(fr, responseColumnName);
      printOutFrameAsTable(fr, false, 10);

      NaiveBayesModel.NaiveBayesParameters parms = new NaiveBayesModel.NaiveBayesParameters();
      parms._train = fr._key;
      parms._laplace = 0;
      parms._response_column = responseColumnName;
      parms._compute_metrics = false;


      model = new NaiveBayes(parms).trainModel().get();

      //Just take some rows from training frame to make prediction for
      Frame testFrame = fr.deepSlice(new long[]{1,2,3,4,5,6,7,8,9}, null);
      printOutFrameAsTable(testFrame, false, testFrame.numRows());

      Frame score = model.score(testFrame);
      Scope.track(score);

      printOutFrameAsTable(score, false, score.numRows());
      assertTrue(model.testJavaScoring(testFrame, score, 1e-5));

    } finally {
      if (model != null) model.delete();
      Scope.exit();
    }
  }
  
  // ColA -enum  ;  ColB - numeric . This will fail quite frequently. If we factor ColB.... test will fail 5-10% of the times. 
  // Don't know yet what our NB implementation is doing with continues variables... but nevertheless it should work in both cases 
  @Test public void testSyntheticWithJavaScoring2() throws IOException {
    NaiveBayesModel model = null;
    Scope.enter();
    long seed = new Random().nextLong();
    try {
        int size = 100000;
        int sizePerChunk = size / 5;
        String[] arr = new String[size];
        for (int a = 0; a < size; a++) {
          arr[a] = Integer.toString(new Random().nextInt(100));
        }
      String responseColumnName = "y";
      Frame fr = new TestFrameBuilder()
                .withName("testFrame")
                .withColNames("ColA","ColB", responseColumnName)
                .withVecTypes(Vec.T_STR, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, arr)
                .withRandomIntDataForCol(1, size, 0, 5, seed) 
                .withRandomIntDataForCol(2, size, 0, 20, seed)
                .withChunkLayout(sizePerChunk, sizePerChunk, sizePerChunk, sizePerChunk, sizePerChunk)
                .build();

        asFactor(fr, "ColA");
//        asFactor(fr, "ColB");    // TODO <-----    comment / uncomment to see the difference
        asFactor(fr, responseColumnName);
        printOutFrameAsTable(fr, false, 10);
        
      NaiveBayesModel.NaiveBayesParameters parms = new NaiveBayesModel.NaiveBayesParameters();
      parms._train = fr._key;
      parms._laplace = 0;
      parms._response_column = responseColumnName;
      parms._compute_metrics = false;


      model = new NaiveBayes(parms).trainModel().get();

      //Just take some rows from training frame to make prediction for
      Frame testFrame = fr.deepSlice(new long[]{1,2,3,4,5,6,7,8,9}, null);
      printOutFrameAsTable(testFrame, false, testFrame.numRows());
      
      Frame score = model.score(testFrame);
      Scope.track(score);
      
      printOutFrameAsTable(score, false, score.numRows());
      assertTrue(model.testJavaScoring(testFrame, score, 1e-5));
      
    } finally {
      if (model != null) model.delete();
      Scope.exit();
    }
  }

  
  // Run it multiple times (with IDE ) with different seeds it fails differently. Actually even with the same seed it fails differently 
  @Test public void highNumberOfResponseClasses() throws IOException {
    NaiveBayesModel model = null;
    Scope.enter();
    long seed = 1234L;//new Random().nextLong();
    System.out.println(seed);
    try {
      int size = 100;
      String[] arr = new String[size];
      Random randomGen = new Random(seed);
      for (int a = 0; a < size; a++) {
        arr[a] = Integer.toString(randomGen.nextInt(3));
      }
      String responseColumnName = "y";
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", responseColumnName)
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, arr)
              .withRandomIntDataForCol(1, size, 0, 4, seed) 
              .build();

      asFactor(fr, "ColA");
      asFactor(fr, responseColumnName);
      printOutFrameAsTable(fr, false, fr.numRows());

      NaiveBayesModel.NaiveBayesParameters parms = new NaiveBayesModel.NaiveBayesParameters();
      parms._train = fr._key;
      parms._laplace = 0;
      parms._response_column = responseColumnName;
      parms._compute_metrics = false;


      model = new NaiveBayes(parms).trainModel().get();

      //Just take some rows from training frame to make prediction for
      Frame testFrame = fr.deepSlice(new long[]{1,2,3,4,5,6,7,8,9}, null);
      printOutFrameAsTable(testFrame, false, testFrame.numRows());

      Frame score = model.score(testFrame);
      Scope.track(score);

      printOutFrameAsTable(score, false, score.numRows());
      assertTrue(model.testJavaScoring(testFrame, score, 1e-5));

    } finally {
      if (model != null) model.delete();
      Scope.exit();
    }
  }

  @Test public void highNumberOfCategoriesInVariable() throws IOException {
    NaiveBayesModel model = null;
    Scope.enter();
    long seed = new Random().nextLong();
    try {
      int size = 100000;
      String[] arr = new String[size];
      for (int a = 0; a < size; a++) {
        arr[a] = Integer.toString(new Random().nextInt(100));
      }
      String responseColumnName = "y";
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", responseColumnName)
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, arr)
              .withRandomIntDataForCol(1, size, 0, 3, seed)
              .build();

      asFactor(fr, "ColA");
      asFactor(fr, responseColumnName);
      printOutFrameAsTable(fr, false, 10);

      NaiveBayesModel.NaiveBayesParameters parms = new NaiveBayesModel.NaiveBayesParameters();
      parms._train = fr._key;
      parms._laplace = 0;
      parms._response_column = responseColumnName;
      parms._compute_metrics = false;


      model = new NaiveBayes(parms).trainModel().get();

      //Just take some rows from training frame to make prediction for
      Frame testFrame = fr.deepSlice(new long[]{1,2,3,4,5,6,7,8,9}, null);
      printOutFrameAsTable(testFrame, false, testFrame.numRows());

      Frame score = model.score(testFrame);
      Scope.track(score);

      printOutFrameAsTable(score, false, score.numRows());
      assertTrue(model.testJavaScoring(testFrame, score, 1e-5));

    } finally {
      if (model != null) model.delete();
      Scope.exit();
    }
  }


  // This is a copy of the test from NaiveBayesTest.
  @Test public void testIris()  {
    NaiveBayesModel model = null;
    Scope.enter();
    try {
      Frame fr = parse_test_file(Key.make("iris_wheader.hex"), "smalldata/iris/iris_wheader.csv");
      asFactor(fr, "sepal_wid");
      asFactor(fr, "petal_len"); // <<------- Comment and uncomment this line and it will fail in one case
      asFactor(fr, "class");
      printOutColumnsMetadata(fr);

      Scope.track(fr);
      NaiveBayesModel.NaiveBayesParameters parms = new NaiveBayesModel.NaiveBayesParameters();
      parms._train = fr._key;
      parms._laplace = 0;
      parms._response_column = fr._names[4];
      parms._compute_metrics = false;

      model = new NaiveBayes(parms).trainModel().get();

      Frame testFrame = fr.deepSlice(new long[]{1,2,3,4,5,6,7,8,9}, null);
      printOutColumnsMetadata(testFrame);
      printOutFrameAsTable(testFrame, false, testFrame.numRows());

      Frame score = model.score(testFrame);
      Scope.track(score);

      printOutFrameAsTable(score, false, score.numRows());
      assertTrue(model.testJavaScoring(testFrame, score, 1e-5));

    } finally {
      if (model != null) model.delete();
    }
  }

  // Consistency test is fine
  @Test public void checkConsistencyTest() throws IOException {
    NaiveBayesModel model = null;
    
    long seed = new Random().nextLong();
    Random randomGen = new Random(seed);
    int size = 100000;

    String[] arr = new String[size];

    for (int a = 0; a < size; a++) {
      arr[a] = Integer.toString(randomGen.nextInt(100));
    }
    Frame score = null;
    for (int i = 0; i < 150; i++) {
//      Scope.enter();
      try {
        
        String responseColumnName = "y";
        Frame fr = new TestFrameBuilder()
                .withName(UUID.randomUUID().toString())
                .withColNames("ColA", responseColumnName)
                .withVecTypes(Vec.T_STR, Vec.T_NUM)
                .withDataForCol(0, arr)
                .withRandomIntDataForCol(1, size, 0, 3, seed)
                .build();

        asFactor(fr, "ColA");
        asFactor(fr, responseColumnName);
        printOutFrameAsTable(fr, false, 10);

        NaiveBayesModel.NaiveBayesParameters parms = new NaiveBayesModel.NaiveBayesParameters();
        parms._train = fr._key;
        parms._laplace = 0;
        parms._response_column = responseColumnName;
        parms._compute_metrics = false;


        model = new NaiveBayes(parms).trainModel().get();

        //Just take some rows from training frame to make prediction for
        Frame testFrame = fr.deepSlice(new long[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, null);
//        Scope.untrack(testFrame.keys());
        printOutFrameAsTable(testFrame, false, testFrame.numRows());

        if(score == null) {
          score = model.score(testFrame);
        }
        else {
          assertTrue(isBitIdentical(score, model.score(testFrame)));
        }

      } finally {
        if (model != null) model.delete();
//        Scope.exit();
      }
    }
  }
}
