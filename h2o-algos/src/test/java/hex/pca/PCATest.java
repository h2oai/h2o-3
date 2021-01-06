package hex.pca;

import hex.DataInfo;
import hex.SplitFrame;
import hex.generic.Generic;
import hex.generic.GenericModel;
import hex.genmodel.MojoPipelineBuilder;
import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.pca.PCAModel.PCAParameters;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import hex.CreateFrame;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Random;

import java.util.concurrent.ExecutionException;

@RunWith(Parameterized.class)
public class PCATest extends TestUtil {
  
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();
  
  public static final double TOLERANCE = 1e-6;
  private PCAParameters pcaParameters;

  @Parameters
  public static PCAImplementation[] parametersForSvdImplementation() {
    return hex.pca.PCAImplementation.values();
  }

  @Parameter
  public PCAImplementation PCAImplementation;

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Before public void setupPcaParameters() {
    pcaParameters = new PCAParameters();
    pcaParameters._pca_implementation = PCAImplementation;
    water.util.Log.info("pcaParameters._PCAImplementation: " + pcaParameters._pca_implementation.name());
  }

  @Test public void testArrestsScoring() throws InterruptedException, ExecutionException {
    // Results with original training frame
    double[] stddev = new double[] {202.7230564, 27.8322637, 6.5230482, 2.5813652};
    double[][] eigvec = ard(ard(-0.04239181, 0.01616262, -0.06588426, 0.99679535),
                            ard(-0.94395706, 0.32068580, 0.06655170, -0.04094568),
                            ard(-0.30842767, -0.93845891, 0.15496743, 0.01234261),
                            ard(-0.10963744, -0.12725666, -0.98347101, -0.06760284));

    PCAModel model = null;
    Frame train = null, score = null, scoreR = null;
    try {
      train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      pcaParameters._train = train._key;
      pcaParameters._k = 4;
      pcaParameters._transform = DataInfo.TransformType.NONE;
      pcaParameters._pca_method = PCAParameters.Method.GramSVD;

      model = new PCA(pcaParameters).trainModel().get();
      TestUtil.checkStddev(stddev, model._output._std_deviation, 1e-5);
      boolean[] flippedEig = TestUtil.checkEigvec(eigvec, model._output._eigenvectors, 1e-5);

      score = model.score(train);
      scoreR = parseTestFile(Key.make("scoreR.hex"), "smalldata/pca_test/USArrests_PCAscore.csv");
//      TestUtil.checkProjection(scoreR, score, TOLERANCE, flippedEig);    // Flipped cols must match those from eigenvectors
      TestUtil.checkProjection(scoreR, score, 1e-6, flippedEig);    // Flipped cols must match those from eigenvectors

      // Build a POJO, validate same results
      Assert.assertTrue(model.testJavaScoring(train,score,1e-5));
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (scoreR != null) scoreR.delete();
      if (model != null) model.delete();
    }
  }

  @Test public void testPCAwithNoK() throws InterruptedException, ExecutionException {
    // Results with original training frame
    Scope.enter();
    PCAModel modelNok = null;
    PCAModel modelK = null;
    Frame train = null, score = null, scoreK = null;
    try {
      train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      Scope.track(train);
      pcaParameters._train = train._key;
      pcaParameters._transform = DataInfo.TransformType.NONE;
      pcaParameters._pca_method = PCAParameters.Method.GramSVD;
      pcaParameters._seed = 12345;
      modelNok = new PCA(pcaParameters).trainModel().get();
      Scope.track_generic(modelNok);
      score = modelNok.score(train);
      Scope.track(score);

      pcaParameters._k=1;
      modelK = new PCA(pcaParameters).trainModel().get();
      Scope.track_generic(modelK);
      scoreK = modelK.score(train);
      Scope.track(scoreK);
      assertBitIdentical(score, scoreK);
    } finally {
      Scope.exit();
    }
  }


  @Test public void testIrisSplitScoring() throws InterruptedException, ExecutionException {
    PCAModel model = null;
    Frame fr = null, fr2= null;
    Frame tr = null, te= null;

    try {
      fr = parseTestFile("smalldata/iris/iris_wheader.csv");
      SplitFrame sf = new SplitFrame(fr,new double[] { 0.5, 0.5 },new Key[] { Key.make("train.hex"), Key.make("test.hex")});

      // Invoke the job
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      tr = DKV.get(ksplits[0]).get();
      te = DKV.get(ksplits[1]).get();

      pcaParameters._train = ksplits[0];
      pcaParameters._valid = ksplits[1];
      pcaParameters._k = 4;
      pcaParameters._max_iterations = 1000;
      pcaParameters._pca_method = PCAParameters.Method.GramSVD;

      model = new PCA(pcaParameters).trainModel().get();

      // Done building model; produce a score column with cluster choices
      fr2 = model.score(te);
      Assert.assertTrue(model.testJavaScoring(te, fr2, 1e-5));
    } finally {
      if( fr  != null ) fr.delete();
      if( fr2 != null ) fr2.delete();
      if( tr  != null ) tr.delete();
      if( te  != null ) te.delete();
      if (model != null) model.delete();
    }
  }

  @Test public void testImputeMissing() throws InterruptedException, ExecutionException {
    Frame train = null;
    double missing_fraction = 0.75;
    long seed = 12345;

    try {
      train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      // Add missing values to the training data
      if (missing_fraction > 0) {
        Frame frtmp = new Frame(Key.<Frame>make(), train.names(), train.vecs());
        DKV.put(frtmp._key, frtmp); // Need to put the frame (to be modified) into DKV for MissingInserter to pick up
        FrameUtils.MissingInserter j = new FrameUtils.MissingInserter(frtmp._key, seed, missing_fraction);
        j.execImpl().get(); // MissingInserter is non-blocking, must block here explicitly
        DKV.remove(frtmp._key); // Delete the frame header (not the data)
      }

      pcaParameters._train = train._key;
      pcaParameters._k = 4;
      pcaParameters._transform = DataInfo.TransformType.NONE;
      pcaParameters._pca_method = PCAModel.PCAParameters.Method.GramSVD;
      pcaParameters._impute_missing = true;   // Don't skip rows with NA entries, but impute using mean of column
      pcaParameters._seed = seed;

      PCAModel pca = null;
      pca = new PCA(pcaParameters).trainModel().get();
      if (pca != null) pca.remove();
    } finally {
      if (train != null) train.delete();
    }
  }

  /* Make sure POJO works if the model is only built from categorical variables (no numeric columns) */
  @Test public void testCatOnlyPUBDEV3988() throws InterruptedException, ExecutionException {
    PCAModel model = null;
    Frame train = null, score = null;
    try {
      train = parseTestFile(Key.make("prostate_cat.hex"), "smalldata/prostate/prostate_cat.csv");
      for (int i = train.numCols() - 1; i > 0; i--) {
        Vec v = train.vec(i);
        if (v.get_type() != Vec.T_CAT) {
          train.remove(i);
          Vec.remove(v._key);
        }
      }
      DKV.put(train);
      pcaParameters._train = train._key;
      pcaParameters._k = 2;
      pcaParameters._transform = DataInfo.TransformType.STANDARDIZE;
      pcaParameters._use_all_factor_levels = true;
      pcaParameters._pca_method = PCAParameters.Method.GramSVD;
      pcaParameters._impute_missing = false;
      pcaParameters._seed = 12345;

      PCA pcaParms = new PCA(pcaParameters);
      model = pcaParms.trainModel().get(); // get normal data
      score = model.score(train);

      // Build a POJO, check results with original PCA
      Assert.assertTrue(model.testJavaScoring(train,score,TOLERANCE));
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) model.delete();
    }
  }

  /* Quick test to make sure changes made to PCA for rank deficient matrices do not cause leakage. */
  @Test public void testPUBDEV3500NoLeakage() throws InterruptedException, ExecutionException {
    Scope.enter();
    Frame train = null;
    try {
      train = parseTestFile(Key.make("prostate_cat.hex"), "smalldata/prostate/prostate_cat.csv");
      Scope.track(train);

      pcaParameters._train = train._key;
      pcaParameters._k = 3;
      pcaParameters._transform = DataInfo.TransformType.NONE;
      pcaParameters._pca_method = PCAModel.PCAParameters.Method.Randomized;
      pcaParameters._impute_missing = true;   // Don't skip rows with NA entries, but impute using mean of column
      pcaParameters._seed = 12345;
      pcaParameters._use_all_factor_levels=true;

      PCAModel pca = null;
      pca = new PCA(pcaParameters).trainModel().get();
      Scope.track_generic(pca);
      Assert.assertTrue(pca._parms._k == pca._output._std_deviation.length);
    } finally {
      Scope.exit();
    }
  }

  /*
  This test will make sure that when the test dataset contains columns that are different from
  the training dataset, a warning should be generated to warn the user.
   */
  @Test public void testIrisScoreWarning() throws InterruptedException, ExecutionException {
    PCAModel model = null;
    Frame fr = null, fr2= null;
    Frame tr = null, te= null;
    Scope.enter();

    try {
      fr = parseTestFile("smalldata/iris/iris_wheader.csv");
      tr = parseTestFile("smalldata/iris/iris_wheader_bad_cnames.csv");
      Scope.track(fr);
      Scope.track(tr);

      PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
      parms._train=fr._key;
 //     parms._valid = tr._key;
      parms._k = 4;
      parms._max_iterations = 1000;
      parms._pca_method = PCAParameters.Method.GramSVD;

      model = new PCA(parms).trainModel().get();
      Scope.track_generic(model);

      // Done building model; produce a score column with cluster choices
      fr2 = model.score(tr);
      Scope.track(fr2);
    } finally {
      Scope.exit();
    }
  }

  @Test public void testArrests() throws InterruptedException, ExecutionException {
    // Results with de-meaned training frame
    double[] stddev = new double[] {83.732400, 14.212402, 6.489426, 2.482790};
    double[][] eigvec = ard(ard(0.04170432, -0.04482166, 0.07989066, -0.99492173),
        ard(0.99522128, -0.05876003, -0.06756974, 0.03893830),
        ard(0.04633575, 0.97685748, -0.20054629, -0.05816914),
        ard(0.07515550, 0.20071807, 0.97408059, 0.07232502));

    // Results with standardized training frame
    double[] stddev_std = new double[] {1.5748783, 0.9948694, 0.5971291, 0.4164494};
    double[][] eigvec_std = ard(ard(-0.5358995, 0.4181809, -0.3412327, 0.64922780),
        ard(-0.5831836, 0.1879856, -0.2681484, -0.74340748),
        ard(-0.2781909, -0.8728062, -0.3780158, 0.13387773),
        ard(-0.5434321, -0.1673186, 0.8177779, 0.08902432));

    Frame train = null;
    try {
      train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");   // TODO: Move this outside loop
      for (DataInfo.TransformType std : new DataInfo.TransformType[] {
          DataInfo.TransformType.DEMEAN,
          DataInfo.TransformType.STANDARDIZE }) {
        PCAModel model = null;
        try {
          pcaParameters._train = train._key;
          pcaParameters._k = 4;
          pcaParameters._transform = std;
          pcaParameters._max_iterations = 1000;
          pcaParameters._pca_method = PCAParameters.Method.Power;

          model = new PCA(pcaParameters).trainModel().get();

          if (std == DataInfo.TransformType.DEMEAN) {
            TestUtil.checkStddev(stddev, model._output._std_deviation, TOLERANCE);
            TestUtil.checkEigvec(eigvec, model._output._eigenvectors, TOLERANCE);
          } else if (std == DataInfo.TransformType.STANDARDIZE) {
            TestUtil.checkStddev(stddev_std, model._output._std_deviation, TOLERANCE);
            TestUtil.checkEigvec(eigvec_std, model._output._eigenvectors, TOLERANCE);
          }
        } finally {
          if( model != null ) model.delete();
        }
      }
    } finally {
      if(train != null) train.delete();
    }
  }

  @Test public void testIrisScoring() throws InterruptedException, ExecutionException {
    // Results with original training frame
    double[] stddev = new double[] {7.88175203, 1.56002774, 0.59189816, 0.25917329, 0.15415273, 0.09381276, 0.04768590};
    double[][] eigvec = ard(ard(-0.03169051, -0.32305860,  0.185100382, -0.12336685, -0.14867156,  0.75932119, -0.496462912),
        ard(-0.04289677,  0.04037565, -0.780961964,  0.19727933,  0.07251338, -0.12216945, -0.572298338),
        ard(-0.05019689,  0.16836717,  0.551432201, -0.07122329,  0.08454116, -0.48327010, -0.647522462),
        ard(-0.74915107, -0.26629420, -0.101102186, -0.48920057,  0.32458460, -0.09176909,  0.067412858),
        ard(-0.37877011, -0.50636060,  0.142219195,  0.69081642, -0.26312992, -0.17811871,  0.041411296),
        ard(-0.51177078,  0.65945159, -0.005079934,  0.04881900, -0.52128288,  0.17038367,  0.006223427),
        ard(-0.16742875,  0.32166036,  0.145893901,  0.47102115,  0.72052968,  0.32523458,  0.020389463));

    PCAModel model = null;
    Frame train = null, score = null, scoreR = null;
    try {
      train = parseTestFile(Key.make("iris.hex"), "smalldata/iris/iris_wheader.csv");
      pcaParameters._train = train._key;
      pcaParameters._k = 7;
      pcaParameters._transform = DataInfo.TransformType.NONE;
      pcaParameters._use_all_factor_levels = true;
      pcaParameters._pca_method = PCAParameters.Method.Power;

      model = new PCA(pcaParameters).trainModel().get();
      TestUtil.checkStddev(stddev, model._output._std_deviation, 1e-5);
      boolean[] flippedEig = TestUtil.checkEigvec(eigvec, model._output._eigenvectors, 1e-5);

      score = model.score(train);
      scoreR = parseTestFile(Key.make("scoreR.hex"), "smalldata/pca_test/iris_PCAscore.csv");
      TestUtil.checkProjection(scoreR, score, TOLERANCE, flippedEig);    // Flipped cols must match those from eigenvectors

      // Build a POJO, validate same results
      Assert.assertTrue(model.testJavaScoring(train,score,1e-5));
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (scoreR != null) scoreR.delete();
      if (model != null) model.delete();
    }
  }

  @Test public void testGram() {
    double[][] x = ard(ard(1, 2, 3), ard(4, 5, 6));
    double[][] xgram = ard(ard(17, 22, 27), ard(22, 29, 36), ard(27, 36, 45));  // X'X
    double[][] xtgram = ard(ard(14, 32), ard(32, 77));    // (X')'X' = XX'

    double[][] xgram_glrm = ArrayUtils.formGram(x, false);
    double[][] xtgram_glrm = ArrayUtils.formGram(x, true);
    Assert.assertArrayEquals(xgram, xgram_glrm);
    Assert.assertArrayEquals(xtgram, xtgram_glrm);
  }
  
  @Test
  public void testPCAPredMojoPojo() {
    Scope.enter();
    try {
      CreateFrame cf = new CreateFrame();
      Random generator = new Random();
      int numRows = 8000;
      int numCols = 8;
      cf.rows= numRows;
      cf.cols = numCols;
      cf.factors=8;
      cf.has_response=false;
      cf.seed = 12345;
      cf.missing_fraction = 0; // frames with NAs will be tested in Python/R unit tests
      System.out.println("Createframe parameters: rows: "+numRows+" cols:"+numCols+" seed: "+cf.seed);

      Frame trainPCA = Scope.track(cf.execImpl().get());
      SplitFrame sf = new SplitFrame(trainPCA, new double[]{0.8,0.2}, new Key[] {Key.make("train.hex"), Key.make("test.hex")});
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      Frame tr = DKV.get(ksplits[0]).get();
      Frame te = DKV.get(ksplits[1]).get();
      Scope.track(tr);
      Scope.track(te);

      PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
      parms._train=tr._key;
      parms._k = 4;
      parms._max_iterations = 1000;
      parms._pca_method = PCAParameters.Method.GramSVD; // will iterate through all methods in python or R unit tests

      PCAModel model = new PCA(parms).trainModel().get();
      Scope.track_generic(model);
      
      Frame pred = model.score(te);
      Scope.track(pred);
      Assert.assertTrue(model.testJavaScoring(te, pred, 1e-6)); // compare Java predict with mojo/pojo here
      
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testPCAPredMojoPojoNumericsOnly() {
    Scope.enter();
    try {
      Frame trainPCA = generateRealOnly(8, 8000, 0.0, 12345);
      SplitFrame sf = new SplitFrame(trainPCA, new double[]{0.8,0.2}, new Key[] {Key.make("train.hex"), Key.make("test.hex")});
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      Frame tr = DKV.get(ksplits[0]).get();
      Frame te = DKV.get(ksplits[1]).get();
      Scope.track(tr);
      Scope.track(te);
      Scope.track(trainPCA);

      PCAModel.PCAParameters parms = new PCAModel.PCAParameters();
      parms._train=tr._key;
      parms._k = 4;
      parms._max_iterations = 1000;
      parms._pca_method = PCAParameters.Method.GramSVD; // will iterate through all methods in python or R unit tests

      PCAModel model = new PCA(parms).trainModel().get();
      Scope.track_generic(model);

      Frame pred = model.score(te);
      Scope.track(pred);
      Assert.assertTrue(model.testJavaScoring(te, pred, 1e-6)); // compare Java predict with mojo/pojo here

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testPCAPipeline() throws IOException {
    try {
      Scope.enter();
      Frame train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      Scope.track(train);

      pcaParameters._train = train._key;
      pcaParameters._k = 4;
      pcaParameters._transform = DataInfo.TransformType.NONE;
      pcaParameters._pca_method = PCAParameters.Method.GramSVD;

      PCAModel pca = new PCA(pcaParameters).trainModel().get();
      Scope.track_generic(pca);

      Frame reduced = pca.score(train);
      Scope.track(reduced);

      KMeansModel.KMeansParameters kmeansParameters = new KMeansModel.KMeansParameters();
      kmeansParameters._train = reduced._key;
      kmeansParameters._k = 2;

      KMeansModel kmeans = new KMeans(kmeansParameters).trainModel().get();
      Scope.track_generic(kmeans);

      Frame clusters = kmeans.score(reduced);
      Scope.track(clusters);

      URI pcaMojoUri = pca.exportMojo(tmp.newFolder("pca").getAbsolutePath() + "/pca.zip", false);
      URI kmeansMojoUri = kmeans.exportMojo(tmp.newFolder("kmeans").getAbsolutePath() + "/kmeans.zip", false);
      File pipelineFile = tmp.newFile("pipeline.zip");

      new MojoPipelineBuilder()
              .addModel("pca", new File(pcaMojoUri))
              .addMapping("PC1", "pca", 0)
              .addMapping("PC2", "pca", 1)
              .addMapping("PC3", "pca", 2)
              .addMapping("PC4", "pca", 3)
              .addMainModel("kmeans", new File(kmeansMojoUri))
              .buildPipeline(pipelineFile);

      GenericModel pipelineModel = Generic.importMojoModel(pipelineFile.getAbsolutePath(), true);
      Scope.track_generic(pipelineModel);
      
      Frame pipelinePreds = pipelineModel.score(train, null, null, false);
      Scope.track(pipelinePreds);

      assertVecEquals(clusters.vec(0), pipelinePreds.vec(0), 0);
    } finally {
      Scope.exit();
    }
  }


}
