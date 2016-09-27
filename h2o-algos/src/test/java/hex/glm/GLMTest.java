package hex.glm;

import hex.*;
import hex.DataInfo.TransformType;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.MissingValuesHandling;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import hex.glm.GLMModel.GLMWeightsFun;
import hex.glm.GLMTask.*;
import org.junit.*;

import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.*;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.util.ArrayUtils;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GLMTest  extends TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  public static void testScoring(GLMModel m, Frame fr) {
    Scope.enter();
    // standard predictions
    Frame fr2 = new Frame(fr);
    Frame preds = Scope.track(m.score(fr2));
    m.adaptTestForTrain(fr2,true,false);
    fr2.remove(fr2.numCols()-1); // remove response
    int p = m._output._dinfo._cats + m._output._dinfo._nums;
    int p2 = fr2.numCols() - (m._output._dinfo._weights?1:0)- (m._output._dinfo._offset?1:0);
    assert p == p2: p + " != " + p2;
    fr2.add(preds.names(),preds.vecs());
    // test score0
    new TestScore0(m,m._output._dinfo._weights,m._output._dinfo._offset).doAll(fr2);
    // test pojo
    if((!m._output._dinfo._weights && !m._output._dinfo._offset))
      Assert.assertTrue(m.testJavaScoring(fr,preds,1e-15));
    Scope.exit();
  }


  // class to test score0 since score0 is now not being called by the standard bulk scoring
  public static class TestScore0 extends MRTask {
    final GLMModel _m;
    final boolean _weights;
    final boolean _offset;

    public TestScore0(GLMModel m, boolean w, boolean o) {_m = m; _weights = w; _offset = o;}

    private void checkScore(long rid, double [] predictions, double [] outputs){
      int start = 0;
      if(_m._parms._family == Family.binomial && Math.abs(predictions[2] - _m.defaultThreshold()) < 1e-10)
        start = 1;
      if(_m._parms._family == Family.multinomial) {
        double [] maxs = new double[2];
        for(int j = 1; j < predictions.length; ++j) {
          if(predictions[j] > maxs[0]) {
            if(predictions[j] > maxs[1]) {
              maxs[0] = maxs[1];
              maxs[1] = predictions[j];
            } else maxs[0] = predictions[j];
          }
        }
        if((maxs[1] - maxs[0]) < 1e-10)
          start = 1;
      }
      for (int j = start; j < predictions.length; ++j)
        assertEquals("mismatch at row " + (rid) + ", p = " + j + ": " + outputs[j] + " != " + predictions[j] + ", predictions = " + Arrays.toString(predictions) + ", output = " + Arrays.toString(outputs), outputs[j], predictions[j], 1e-6);
    }
    @Override public void map(Chunk [] chks) {
      int nout = _m._parms._family == Family.multinomial ? _m._output.nclasses() + 1 : _m._parms._family == Family.binomial ? 3 : 1;
      Chunk[] outputChks = Arrays.copyOfRange(chks, chks.length - nout, chks.length);
      chks = Arrays.copyOf(chks, chks.length - nout);
      Chunk off = new C0DChunk(0, chks[0]._len);
      Chunk w = new C0DChunk(1, chks[0]._len);
      double[] tmp = new double[_m._output._dinfo._cats + _m._output._dinfo._nums];
      double[] predictions = new double[nout];
      double[] outputs = new double[nout];
      if (_offset) {
        off = chks[chks.length - 1];
        chks = Arrays.copyOf(chks, chks.length - 1);
      }
      if (_weights) {
        w = chks[chks.length - 1];
        chks = Arrays.copyOf(chks, chks.length - 1);
      }
      for (int i = 0; i < chks[0]._len; ++i) {
        if (_weights || _offset)
          _m.score0(chks, w.atd(i), off.atd(i), i, tmp, predictions);
        else
          _m.score0(chks, i, tmp, predictions);
        for (int j = 0; j < predictions.length; ++j)
          outputs[j] = outputChks[j].atd(i);
        checkScore(i + chks[0].start(), predictions, outputs);
      }
    }
  }
  //------------------- simple tests on synthetic data------------------------------------
  @Test
  public void testGaussianRegression() throws InterruptedException, ExecutionException {
    Key raw = Key.make("gaussian_test_data_raw");
    Key parsed = Key.make("gaussian_test_data_parsed");
    GLMModel model = null;
    Frame fr = null, res = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      FVecTest.makeByteVec(raw, "x,y\n0,0\n1,0.1\n2,0.2\n3,0.3\n4,0.4\n5,0.5\n6,0.6\n7,0.7\n8,0.8\n9,0.9");
      fr = ParseDataset.parse(parsed, raw);
      GLMParameters params = new GLMParameters(Family.gaussian);
      params._train = fr._key;
      // params._response = 1;
      params._response_column = fr._names[1];
      params._lambda = new double[]{0};
//      params._standardize= false;
      model = new GLM(params).trainModel().get();
      HashMap<String, Double> coefs = model.coefficients();
      assertEquals(0.0, coefs.get("Intercept"), 1e-4);
      assertEquals(0.1, coefs.get("x"), 1e-4);
      testScoring(model,fr);
    } finally {
      if (fr != null) fr.remove();
      if (res != null) res.remove();
      if (model != null) model.remove();
    }
  }

  /**
   * Test Poisson regression on simple and small synthetic dataset.
   * Equation is: y = exp(x+1);
   */
  @Test
  public void testPoissonRegression() throws InterruptedException, ExecutionException {
    Key raw = Key.make("poisson_test_data_raw");
    Key parsed = Key.make("poisson_test_data_parsed");

    GLMModel model = null;
    Frame fr = null, res = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      FVecTest.makeByteVec(raw, "x,y\n0,2\n1,4\n2,8\n3,16\n4,32\n5,64\n6,128\n7,256");
      fr = ParseDataset.parse(parsed, raw);
      Vec v = fr.vec(0);
      System.out.println(v.min() + ", " + v.max() + ", mean = " + v.mean());
      GLMParameters params = new GLMParameters(Family.poisson);
      params._train = fr._key;
      // params._response = 1;
      params._response_column = fr._names[1];
      params._lambda = new double[]{0};
      params._standardize = false;
      model = new GLM(params).trainModel().get();
      for (double c : model.beta())
        assertEquals(Math.log(2), c, 1e-2); // only 1e-2 precision cause the perfect solution is too perfect -> will trigger grid search
      testScoring(model,fr);
      model.delete();
      fr.delete();

      // Test 2, example from http://www.biostat.umn.edu/~dipankar/bmtry711.11/lecture_13.pdf
      FVecTest.makeByteVec(raw, "x,y\n1,0\n2,1\n3,2\n4,3\n5,1\n6,4\n7,9\n8,18\n9,23\n10,31\n11,20\n12,25\n13,37\n14,45\n150,7.193936e+16\n");
      fr = ParseDataset.parse(parsed, raw);
      GLMParameters params2 = new GLMParameters(Family.poisson);
      params2._train = fr._key;
      // params2._response = 1;
      params2._response_column = fr._names[1];
      params2._lambda = new double[]{0};
      params2._standardize = true;
      params2._beta_epsilon = 1e-5;
      model = new GLM(params2).trainModel().get();
      assertEquals(0.3396, model.beta()[1], 1e-1);
      assertEquals(0.2565, model.beta()[0], 1e-1);
      // test scoring
      testScoring(model,fr);
    } finally {
      if (fr != null) fr.delete();
      if (res != null) res.delete();
      if (model != null) model.delete();
    }
  }


  /**
   * Test Gamma regression on simple and small synthetic dataset.
   * Equation is: y = 1/(x+1);
   *
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Test
  public void testGammaRegression() throws InterruptedException, ExecutionException {
    GLMModel model = null;
    Frame fr = null, res = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      Key raw = Key.make("gamma_test_data_raw");
      Key parsed = Key.make("gamma_test_data_parsed");
      FVecTest.makeByteVec(raw, "x,y\n0,1\n1,0.5\n2,0.3333333\n3,0.25\n4,0.2\n5,0.1666667\n6,0.1428571\n7,0.125");
      fr = ParseDataset.parse(parsed, raw);
//      /public GLM2(String desc, Key dest, Frame src, Family family, Link link, double alpha, double lambda) {
//      double [] vals = new double[] {1.0,1.0};
      //public GLM2(String desc, Key dest, Frame src, Family family, Link link, double alpha, double lambda) {
      GLMParameters params = new GLMParameters(Family.gamma);
      // params._response = 1;
      params._response_column = fr._names[1];
      params._train = parsed;
      params._lambda = new double[]{0};
      model = new GLM(params).trainModel().get();
      for (double c : model.beta()) assertEquals(1.0, c, 1e-4);
      // test scoring
      testScoring(model,fr);
    } finally {
      if (fr != null) fr.delete();
      if (res != null) res.delete();
      if (model != null) model.delete();
    }
  }

////  //simple tweedie test
//  @Test public void testTweedieRegression() throws InterruptedException, ExecutionException{
//    Key raw = Key.make("gaussian_test_data_raw");
//    Key parsed = Key.make("gaussian_test_data_parsed");
//    Key<GLMModel> modelKey = Key.make("gaussian_test");
//    Frame fr = null;
//    GLMModel model = null;
//    try {
//      // make data so that the expected coefficients is icept = col[0] = 1.0
//      FVecTest.makeByteVec(raw, "x,y\n0,0\n1,0.1\n2,0.2\n3,0.3\n4,0.4\n5,0.5\n6,0.6\n7,0.7\n8,0.8\n9,0.9\n0,0\n1,0\n2,0\n3,0\n4,0\n5,0\n6,0\n7,0\n8,0\n9,0");
//      fr = ParseDataset.parse(parsed, new Key[]{raw});
//      double [] powers = new double [] {1.5,1.1,1.9};
//      double [] intercepts = new double []{3.643,1.318,9.154};
//      double [] xs = new double []{-0.260,-0.0284,-0.853};
//      for(int i = 0; i < powers.length; ++i){
//        DataInfo dinfo = new DataInfo(fr, 1, false, DataInfo.TransformType.NONE);
//        GLMParameters glm = new GLMParameters(Family.tweedie);
//
//        new GLM2("GLM test of gaussian(linear) regression.",Key.make(),modelKey,dinfo,glm,new double[]{0},0).fork().get();
//        model = DKV.get(modelKey).get();
//        testHTML(model);
//        HashMap<String, Double> coefs = model.coefficients();
//        assertEquals(intercepts[i],coefs.get("Intercept"),1e-3);
//        assertEquals(xs[i],coefs.get("x"),1e-3);
//      }
//    }finally{
//      if( fr != null ) fr.delete();
//      if(model != null)model.delete();
//    }
//  }


  @Test
  public void testAllNAs() {
    Key raw = Key.make("gamma_test_data_raw");
    Key parsed = Key.make("gamma_test_data_parsed");
    FVecTest.makeByteVec(raw, "x,y,z\n1,0,NA\n2,NA,1\nNA,3,2\n4,3,NA\n5,NA,1\nNA,6,4\n7,NA,9\n8,NA,18\nNA,9,23\n10,31,NA\nNA,11,20\n12,NA,25\nNA,13,37\n14,45,NA\n");
    Frame fr = ParseDataset.parse(parsed, raw);
    GLM job = null;
    try {
      GLMParameters params = new GLMParameters(Family.poisson);
      // params._response = 1;
      params._response_column = fr._names[1];
      params._train = parsed;
      params._lambda = new double[]{0};
      params._missing_values_handling = MissingValuesHandling.Skip;
      GLM glm = new GLM( params);
      glm.trainModel().get();
      assertFalse("should've thrown IAE", true);
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("No rows left in the dataset"));
    } finally {
      fr.delete();
    }
  }

  // Make sure all three implementations of ginfo computation in GLM get the same results
  @Test
  public void testGradientTask() {
    Key parsed = Key.make("cars_parsed");
    Frame fr = null;
    DataInfo dinfo = null;
    try {
      fr = parse_test_file(parsed, "smalldata/junit/mixcat_train.csv");
      GLMParameters params = new GLMParameters(Family.binomial, Family.binomial.defaultLink, new double[]{0}, new double[]{0}, 0, 0);
      // params._response = fr.find(params._response_column);
      params._train = parsed;
      params._lambda = new double[]{0};
      params._use_all_factor_levels = true;
      fr.add("Useless", fr.remove("Useless"));

      dinfo = new DataInfo(fr, null, 1, params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false, false, false);
      DKV.put(dinfo._key,dinfo);
      double [] beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      Random rnd = new Random(987654321);
      for (int i = 0; i < beta.length; ++i)
        beta[i] = 1 - 2 * rnd.nextDouble();

      GLMGradientTask grtSpc = new GLMBinomialGradientTask(null,dinfo, params, params._lambda[0], beta).doAll(dinfo._adaptedFrame);
      GLMGradientTask grtGen = new GLMGenericGradientTask(null,dinfo, params, params._lambda[0], beta).doAll(dinfo._adaptedFrame);
      for (int i = 0; i < beta.length; ++i)
        assertEquals("gradients differ", grtSpc._gradient[i], grtGen._gradient[i], 1e-4);
      params = new GLMParameters(Family.gaussian, Family.gaussian.defaultLink, new double[]{0}, new double[]{0}, 0, 0);
      params._use_all_factor_levels = false;
      dinfo.remove();
      dinfo = new DataInfo(fr, null, 1, params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false, false, false);
      DKV.put(dinfo._key,dinfo);
      beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      rnd = new Random(1987654321);
      for (int i = 0; i < beta.length; ++i)
        beta[i] = 1 - 2 * rnd.nextDouble();
      grtSpc = new GLMGaussianGradientTask(null,dinfo, params, params._lambda[0], beta).doAll(dinfo._adaptedFrame);
      grtGen = new GLMGenericGradientTask(null,dinfo, params, params._lambda[0], beta).doAll(dinfo._adaptedFrame);
      for (int i = 0; i < beta.length; ++i)
        assertEquals("gradients differ: " + Arrays.toString(grtSpc._gradient) + " != " + Arrays.toString(grtGen._gradient), grtSpc._gradient[i], grtGen._gradient[i], 1e-4);
      dinfo.remove();
    } finally {
      if (fr != null) fr.delete();
      if (dinfo != null) dinfo.remove();
    }
  }


  @Test
  public void testMultinomialGradient(){
    Key parsed = Key.make("covtype");

    Frame fr = null;

    double [][] beta = new double[][]{
      {
         5.886754459, -0.270479620, -0.075466082, -0.157524534, -0.225843747, -0.975387326, -0.018808013, -0.597839451,  0.931896624,  1.060006010,
         1.513888539,  0.588802780,  0.157815155, -2.158268564, -0.504962385, -1.218970183, -0.840958642, -0.425931637, -0.355548831, -0.845035489,
        -0.065364107,  0.215897656,  0.213009374,  0.006831714,  1.212368946,  0.006106444, -0.350643486, -0.268207009, -0.252099054, -1.374010836,
         0.257935860,  0.397459631,  0.411530391,  0.728368253,  0.292076224,  0.170774269, -0.059574793,  0.273670163,  0.180844505, -0.186483071,
         0.369186813,  0.161909512,  0.249411716, -0.094481604,  0.413354360, -0.419043967,  0.044517794, -0.252596992, -0.371926422,  0.253835004,
         0.588162090,  0.123330837,  2.856812217
      },
      {
         1.89790254, -0.29776886,  0.15613197,  0.37602123, -0.36464436, -0.30240244, -0.57284370,  0.62408956, -0.22369305,  0.33644602,  0.79886400,
         0.65351945, -0.53682819, -0.58319898, -1.07762513, -0.28527470,  0.46563482, -0.76956081, -0.72513805,  0.29857876,  0.03993456,  0.15835864,
        -0.24797599, -0.02483503,  0.93822490, -0.12406087, -0.75837978, -0.23516944, -0.48520212,  0.73571466,  0.19652011,  0.21602846, -0.32743154,
         0.49421903, -0.02262943,  0.08093216,  0.11524497,  0.21657128,  0.18072853,  0.30872666,  0.17947687,  0.20156151,  0.16812179, -0.12286908,
         0.29630502,  0.09992565, -0.00603293,  0.20700058, -0.49706211, -0.14534034, -0.18819217,  0.03642680,  7.31828340
      },
      {
        -6.098728943,  0.284144173,  0.114373474,  0.328977319,  0.417830082,  0.285696150, -0.652674822,  0.319136906, -0.942440279, -1.619235397,
        -1.272568201, -0.079855555,  1.191263550,  0.205102353,  0.991773314,  0.930363203,  1.014021007,  0.651243292,  0.646532457,  0.914336030,
         0.012171754, -0.053042102,  0.777710362,  0.527369151, -0.019496049,  0.186290583,  0.554926655,  0.476911685,  0.529207520, -0.133243060,
        -0.198957274, -0.561552913, -0.069239959, -0.236600870, -0.969503908, -0.848089244,  0.001498592, -0.241007311, -0.129271912, -0.259961677,
        -0.895676033, -0.865827509, -0.972629899,  0.307756211, -1.809423763, -0.199557594,  0.024221965, -0.024834485,  0.047044475,  0.028951561,
        -0.157701002,  0.007940593, -2.073329675,
      },
      {
        -8.36044440,  0.10541672, -0.01628680, -0.43787017,  0.42383466,  2.45802808,  0.59818831,  0.61971728, -0.62598983,  0.20261555, -0.21909545,
         0.35125447, -3.29155913,  3.74668257,  0.18126128, -0.13948924,  0.20465077, -0.39930635,  0.15704570, -0.01036891,  0.02822546, -0.02349234,
        -0.93922249, -0.20025910,  0.25184125,  0.06415974,  0.35271290,  0.04609060,  0.03018497, -0.10641540,  0.00354805, -0.12194129,  0.05115876,
         0.23981864, -0.10007012,  0.04773226,  0.01217421,  0.02367464,  0.05552397,  0.05343606, -0.05818705, -0.30055029, -0.03898723,  0.02322906,
        -0.04908215,  0.04274038,  0.25045428,  0.08561191,  0.15228160,  0.67005377,  0.59311621,  0.58814959, -4.83776046
      },
      {
        -0.39251919,  0.07053038,  0.09397355,  0.19394977, -0.02030732, -0.87489691,  0.21295049,  0.31800509, -0.05347208, -1.03491602,  2.20106706,
        -1.20895873,  1.06158893, -3.29214054, -0.69334082,  0.62309414, -1.64753442,  0.10189669, -0.44746013, -1.04084383, -0.01997483, -0.23356180,
         0.34384724,  0.37566329, -1.79316510,  0.46183758, -0.58814389,  0.12072985,  0.48349078,  1.18956325,  0.41962148,  0.18767160, -0.25252495,
        -1.13671540,  0.71488183,  0.27405258, -0.03527945,  0.43124949, -0.28740586,  0.35165348,  1.17594079,  1.13893507,  0.49423372,  0.30525649,
         0.70809680,  0.16660330, -0.37726163, -0.14687217, -0.17079711, -1.01897715, -1.17494223, -0.72698683,  1.64022531
      },
      {
        -5.892381502,  0.295534637, -0.112763568,  0.080283203,  0.197113227,  0.525435203,  0.727252262, -1.190672917,  1.137103389, -0.648526151,
        -2.581362158, -0.268338673,  2.010179009,  0.902074450,  0.816138328,  0.557071470,  0.389932578,  0.009422297,  0.542270816,  0.550653667,
         0.005211720, -0.071954379,  0.320008238,  0.155814784, -0.264213966,  0.320538295,  0.569730803,  0.444518874,  0.247279544, -0.319484330,
        -0.372129988,  0.340944707, -0.158424299, -0.479426774,  0.026966661,  0.273389077, -0.004744599, -0.339321329, -0.119323949, -0.210123558,
        -1.218998166, -0.740525896,  0.134778587,  0.252701229,  0.527468284,  0.214164427, -0.080104361, -0.021448994,  0.004509104, -0.189729053,
        -0.335041198, -0.080698796, -1.192518082
      },
      {
        12.9594170391, -0.1873774300, -0.1599625360, -0.3838368119, -0.4279825390, -1.1164727575, -0.2940645257, -0.0924364781, -0.2234047720,  1.7036099945,
        -0.4407937881, -0.0364237384, -0.5924593214,  1.1797487023,  0.2867554171, -0.4667946900,  0.4142538835,  0.8322365174,  0.1822980332,  0.1326797653,
        -0.0002045542,  0.0077943238, -0.4673767424, -0.8405848140, -0.3255599769, -0.9148717663,  0.2197967986, -0.5848745645, -0.5528616430,  0.0078757154,
        -0.3065382365, -0.4586101971,  0.3449315968,  0.3903371200,  0.0582787537,  0.0012089013, -0.0293189213, -0.3648369414,  0.1189047254, -0.0572478953,
         0.4482567793,  0.4044976082, -0.0349286763, -0.6715923088, -0.0867185553,  0.0951677966,  0.1442048837,  0.1531401571,  0.8359504674,  0.4012062075,
         0.6745982951,  0.0518378060, -3.7117127004
      }
    };
    double [] exp_grad = new double[]{
      -8.955455e-05, 6.429112e-04, 4.384381e-04, 1.363695e-03, 4.714468e-04, -2.264769e-03, 4.412849e-04, 1.461760e-03, -2.957754e-05, -2.244325e-03,
      -2.744438e-03, 9.109376e-04, 1.920764e-03, 7.562221e-04, 1.840414e-04, 2.455081e-04, 3.077885e-04, 2.833261e-04, 1.248686e-04, 2.509248e-04,
      9.681260e-06, -1.097335e-04, 1.005934e-03, 5.623159e-04, -2.568397e-03, 1.113900e-03, 1.263858e-04, 9.075801e-05, 8.056571e-05, 1.848318e-04,
      -1.291357e-04, -3.710570e-04, 5.693621e-05, 1.328082e-04, 3.244018e-04, 4.130594e-04, 9.681066e-06, 5.215260e-04, 4.054695e-04, 2.904901e-05,
      -3.074865e-03, -1.247025e-04, 1.044981e-03, 8.612937e-04, 1.376526e-03, 4.543256e-05, -4.596319e-06, 3.062111e-05, 5.649646e-05, 5.392599e-04,
      9.681357e-04, 2.298219e-04, -1.369109e-03,

      -6.884926e-04, -9.921529e-04, -5.369346e-04, -1.732447e-03, 5.677645e-04, 1.655432e-03, -4.786890e-04, -8.688757e-04, 2.922016e-04, 3.601210e-03,
      4.050781e-03, -6.409806e-04, -2.788663e-03, -1.426483e-03, -1.946904e-04, -8.279536e-04, -3.148338e-04, 2.263577e-06, -1.320917e-04, 3.635088e-04,
      -1.024655e-05, 1.079612e-04, -1.607591e-03, -1.801967e-04, 2.548311e-03, -1.007139e-03, -1.336990e-04, 2.538803e-04, -4.851292e-04, -9.168206e-04,
      1.027708e-04, 1.061545e-03, -4.098038e-05, 1.070448e-04, 3.220238e-04, -7.011285e-04, -1.024153e-05, -7.967380e-04, -2.708138e-04, -2.698165e-04,
      3.088978e-03, 4.260939e-04, -5.868815e-04, -1.562233e-03, -1.007565e-03, -2.034456e-04, -6.198011e-04, -3.277194e-05, -5.976557e-05, -1.143198e-03,
      -1.025416e-03, 3.671158e-04, 1.448332e-03,

      1.940231e-03, -6.130695e-04, -2.086460e-03, -2.969848e-04, 1.455597e-04, 1.745515e-03, 2.123991e-03, 9.036201e-04, -5.270206e-04, 1.053891e-03,
      1.358911e-03, 2.528711e-04, 1.326987e-04, -1.825879e-03, -6.085616e-04, -1.347628e-04, 3.499544e-04, 3.616313e-04, -7.008672e-04, -1.211077e-03,
      1.117824e-05, 3.535679e-05, -2.668903e-03, -2.399884e-04, 3.979678e-04, 2.519517e-04, 1.113206e-04, 6.029871e-04, 3.512828e-04, 2.134159e-04,
      7.590052e-05, 1.729959e-04, 4.472972e-05, 2.094373e-04, 3.136961e-04, 1.835530e-04, 1.117824e-05, 8.225263e-05, 4.330828e-05, 3.354142e-05,
      7.452883e-04, 4.631413e-04, 2.054077e-04, -5.520636e-05, 2.818063e-04, 5.246077e-05, 1.131811e-04, 3.535664e-05, 6.523360e-05, 3.072416e-04,
      2.913399e-04, 2.422760e-04, -1.580841e-03,

      -1.117356e-04, 2.573351e-04, 8.117137e-04, 1.168873e-04, -4.216143e-04, -5.847717e-05, 3.501109e-04, 2.344622e-04, -1.330097e-04, -5.948309e-04,
      -2.349808e-04, -4.495448e-05, -1.916493e-04, 5.017336e-04, -8.440468e-05, 4.767465e-04, 2.485018e-04, 2.060573e-04, -1.527142e-04, -9.268231e-06,
      -1.985972e-06, -6.285478e-06, -2.214673e-05, 5.822250e-04, -7.069316e-05, -4.387924e-05, -2.774128e-04, -5.455282e-04, 3.186328e-04, -3.793242e-05,
      -1.349306e-05, -3.070112e-05, -7.951882e-06, -3.723186e-05, -5.571437e-05, -3.260780e-05, -1.987225e-06, -1.462245e-05, -7.699184e-06, -5.962867e-06,
      -1.316053e-04, -8.108570e-05, -3.651228e-05, -5.312255e-05, -5.009791e-05, -9.325808e-06, -2.012086e-05, -6.285571e-06, -1.159698e-05, -5.462022e-05,
      -5.179310e-05, -4.307092e-05, 2.810360e-04,

      3.869942e-04, -3.450936e-05, -7.805675e-05, 6.405561e-04, -2.284402e-04, -1.866295e-04, -4.858359e-04, 3.496890e-04, 7.352780e-04, 5.767877e-04,
      -8.477014e-04, -5.512698e-05, 1.091158e-03, -1.900036e-04, -4.632766e-05, 1.086153e-05, -7.743051e-05, -7.545391e-04, -3.143243e-05, -6.316374e-05,
      -2.435782e-06, -7.707894e-06, 4.451785e-04, 2.043479e-04, -8.673378e-05, -3.314975e-05, -3.181369e-05, -5.422704e-04, -9.020739e-05, 6.747588e-04,
      5.997742e-06, -9.729086e-04, -9.751490e-06, -4.565744e-05, -4.181943e-04, 7.522183e-04, -2.436958e-06, 2.531532e-04, -9.441600e-06, 2.317743e-04,
      4.254207e-04, -3.224488e-04, 3.979052e-04, 2.066697e-04, 2.486194e-05, 1.189306e-04, -2.465884e-05, -7.708071e-06, -1.422152e-05, -6.697064e-05,
      -6.351172e-05, -5.281060e-05, 3.446379e-04,

      -1.212986e-03, 9.206612e-04, 6.469824e-04, -6.605882e-04, -1.646537e-05, -6.854543e-04, -2.079925e-03, -1.031449e-03, 3.926585e-04, -1.556234e-03,
      -1.129748e-03, -2.113480e-04, -4.922559e-04, 1.938461e-03, 6.900824e-04, 1.497533e-04, -6.140808e-04, -3.365137e-04, 8.516225e-04, 5.874586e-04,
      -9.342693e-06, -2.955083e-05, 2.692614e-03, -9.928211e-04, -3.326157e-04, -3.572773e-04, 1.641113e-04, 7.442831e-05, -2.543959e-04, -1.783712e-04,
      -6.343638e-05, 9.077554e-05, -3.738480e-05, -1.750387e-04, -6.568480e-04, -2.035799e-04, -9.342694e-06, -6.874421e-05, -3.619677e-05, -2.803369e-05,
      -6.228932e-04, -3.870861e-04, -1.103792e-03, 9.585360e-04, -7.037269e-05, 2.736606e-04, -9.459508e-05, -2.955084e-05, -5.452180e-05, -2.567899e-04,
      -2.434930e-04, -2.024919e-04, 1.321256e-03,

      -2.244563e-04, -1.811758e-04,  8.043173e-04,  5.688820e-04, -5.182511e-04, -2.056167e-04, 1.290635e-04, -1.049207e-03, -7.305304e-04, -8.364983e-04,
      -4.528248e-04, -2.113987e-04,  3.279472e-04,  2.459491e-04,  5.986061e-05,  7.984705e-05, 1.001005e-04,  2.377746e-04,  4.061439e-05,  8.161668e-05,
       3.151497e-06,  9.959707e-06,  1.549140e-04,  6.411739e-05,  1.121613e-04,  7.559378e-05, 4.110778e-05,  6.574476e-05,  7.925128e-05,  6.011770e-05,
       2.139605e-05,  4.934971e-05, -5.597385e-06, -1.913622e-04,  1.706349e-04, -4.115145e-04, 3.149101e-06,  2.317293e-05, -1.246264e-04,  9.448371e-06,
      -4.303234e-04,  2.608783e-05,  7.889196e-05, -3.559375e-04, -5.551586e-04, -2.777131e-04, 6.505911e-04,  1.033867e-05,  1.837583e-05,  6.750772e-04,
       1.247379e-04, -5.408403e-04,  -4.453114e-04,
    };
  Vec origRes = null;
    try {
      fr = parse_test_file(parsed, "smalldata/covtype/covtype.20k.data");
      fr.remove("C21").remove();
      fr.remove("C29").remove();
      GLMParameters params = new GLMParameters(Family.multinomial);
      params._response_column = "C55";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{};
      params._train = parsed;
      params._lambda = new double[]{0};
      params._alpha = new double[]{0};
      origRes = fr.remove("C55");
      Vec res = fr.add("C55",origRes.toCategoricalVec());
      double [] means = new double [res.domain().length];
      long [] bins = res.bins();
      double sumInv = 1.0/ArrayUtils.sum(bins);
      for(int i = 0; i < bins.length; ++i)
        means[i] = bins[i]*sumInv;
      DataInfo dinfo = new DataInfo(fr, null, 1, true, TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true, false, false, false, false, false);
      GLMTask.GLMMultinomialGradientTask gmt = new GLMTask.GLMMultinomialGradientTask(null,dinfo,0,beta,1.0/fr.numRows()).doAll(dinfo._adaptedFrame);
      assertEquals(0.6421113,gmt._likelihood/fr.numRows(),1e-8);
      System.out.println("likelihood = " + gmt._likelihood/fr.numRows());
      double [] g = gmt.gradient();
      for(int i = 0; i < g.length; ++i)
        assertEquals("Mismatch at coefficient '" + "' (" + i + ")",exp_grad[i], g[i], 1e-8);
    } finally {
      if(origRes != null)origRes.remove();
      if (fr != null) fr.delete();
    }
  }
  //------------ TEST on selected files form small data and compare to R results ------------------------------------

  /**
   * Simple test for poisson, gamma and gaussian families (no regularization, test both lsm solvers).
   * Basically tries to predict horse power based on other parameters of the cars in the dataset.
   * Compare against the results from standard R glm implementation.
   *
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Test
  public void testCars() throws InterruptedException, ExecutionException {
    Scope.enter();
    Key parsed = Key.make("cars_parsed");
    Frame fr = null;
    GLMModel model = null;
    Frame score = null;
    try {
      fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
      GLMParameters params = new GLMParameters(Family.poisson, Family.poisson.defaultLink, new double[]{0}, new double[]{0},0,0);
      params._response_column = "power (hp)";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"name"};
      params._train = parsed;
      params._lambda = new double[]{0};
      params._alpha = new double[]{0};
      params._missing_values_handling = MissingValuesHandling.Skip;

      model = new GLM( params).trainModel().get();
      HashMap<String, Double> coefs = model.coefficients();
      String[] cfs1 = new String[]{"Intercept", "economy (mpg)", "cylinders", "displacement (cc)", "weight (lb)", "0-60 mph (s)", "year"};
      double[] vls1 = new double[]{4.9504805, -0.0095859, -0.0063046, 0.0004392, 0.0001762, -0.0469810, 0.0002891};
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls1[i], coefs.get(cfs1[i]), 1e-4);
      // test gamma
      double[] vls2 = new double[]{8.992e-03, 1.818e-04, -1.125e-04, 1.505e-06, -1.284e-06, 4.510e-04, -7.254e-05};
      testScoring(model,fr);
      model.delete();
      params = new GLMParameters(Family.gamma, Family.gamma.defaultLink, new double[]{0}, new double[]{0},0,0);
      params._response_column = "power (hp)";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"name"};
      params._train = parsed;
      params._lambda = new double[]{0};
      params._beta_epsilon = 1e-5;
      params._missing_values_handling = MissingValuesHandling.Skip;
      model = new GLM( params).trainModel().get();
      coefs = model.coefficients();
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls2[i], coefs.get(cfs1[i]), 1e-4);
      testScoring(model,fr);
      model.delete();
      // test gaussian
      double[] vls3 = new double[]{166.95862, -0.00531, -2.46690, 0.12635, 0.02159, -4.66995, -0.85724};
      params = new GLMParameters(Family.gaussian);
      params._response_column = "power (hp)";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"name"};
      params._train = parsed;
      params._lambda = new double[]{0};
      params._missing_values_handling = MissingValuesHandling.Skip;
      model = new GLM( params).trainModel().get();
      coefs = model.coefficients();
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls3[i], coefs.get(cfs1[i]), 1e-4);
      // test scoring
    } finally {
      if (fr != null) fr.delete();
      if (score != null) score.delete();
      if (model != null) model.delete();
      Scope.exit();
    }
  }

  // Leask xval keys
//  @Test public void testXval() {
//    GLMModel model = null;
//    Frame fr = parse_test_file("smalldata/glm_test/prostate_cat_replaced.csv");
//    Frame score = null;
//    try{
//      Scope.enter();
//      // R results
////      Coefficients:
////        (Intercept)           ID          AGE       RACER2       RACER3        DPROS        DCAPS          PSA          VOL      GLEASON
////          -8.894088     0.001588    -0.009589     0.231777    -0.459937     0.556231     0.556395     0.027854    -0.011355     1.010179
//      String [] cfs1 = new String [] {"Intercept","AGE", "RACE.R2","RACE.R3", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"};
//      double [] vals = new double [] {-8.14867, -0.01368, 0.32337, -0.38028, 0.55964, 0.49548, 0.02794, -0.01104, 0.97704};
//      GLMParameters params = new GLMParameters(Family.binomial);
//      params._n_folds = 10;
//      params._response_column = "CAPSULE";
//      params._ignored_columns = new String[]{"ID"};
//      params._train = fr._key;
//      params._lambda = new double[]{0};
//      model = new GLM(params,Key.make("prostate_model")).trainModel().get();
//      HashMap<String, Double> coefs = model.coefficients();
//      for(int i = 0; i < cfs1.length; ++i)
//        assertEquals(vals[i], coefs.get(cfs1[i]),1e-4);
//      GLMValidation val = model.trainVal();
////      assertEquals(512.3, val.nullDeviance(),1e-1);
////      assertEquals(378.3, val.residualDeviance(),1e-1);
////      assertEquals(396.3, val.AIC(),1e-1);
////      score = model.score(fr);
////
////      hex.ModelMetrics mm = hex.ModelMetrics.getFromDKV(model,fr);
////
////      AUCData adata = mm._aucdata;
////      assertEquals(val.auc(),adata.AUC(),1e-2);
////      GLMValidation val2 = new GLMValidationTsk(params,model._ymu,rank(model.beta())).doAll(new Vec[]{fr.vec("CAPSULE"),score.vec("1")})._val;
////      assertEquals(val.residualDeviance(),val2.residualDeviance(),1e-6);
////      assertEquals(val.nullDeviance(),val2.nullDeviance(),1e-6);
//    } finally {
//      fr.delete();
//      if(model != null)model.delete();
//      if(score != null)score.delete();
//      Scope.exit();
//    }
//  }

  /**
   * Test bounds on prostate dataset, 2 cases :
   * 1) test against known result in glmnet (with elastic net regularization) with elastic net penalty
   * 2) test with no regularization, check the ginfo in the end.
   */
  @Test
  public void testBounds() {
//    glmnet's result:
//    res2 <- glmnet(x=M,y=D$CAPSULE,lower.limits=-.5,upper.limits=.5,family='binomial')
//    res2$beta[,58]
//    AGE        RACE          DPROS       PSA         VOL         GLEASON
//    -0.00616326 -0.50000000  0.50000000  0.03628192 -0.01249324  0.50000000 //    res2$a0[100]
//    res2$a0[58]
//    s57
//    -4.155864
//    lambda = 0.001108, null dev =  512.2888, res dev = 379.7597
    GLMModel model = null;

    Key parsed = Key.make("prostate_parsed");
    Key modelKey = Key.make("prostate_model");

    Frame fr = parse_test_file(parsed, "smalldata/logreg/prostate.csv");
    Key betaConsKey = Key.make("beta_constraints");

    String[] cfs1 = new String[]{"AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON", "Intercept"};
    double[] vals = new double[]{-0.006502588, -0.500000000, 0.500000000, 0.400000000, 0.034826559, -0.011661747, 0.500000000, -4.564024};

//    [AGE, RACE, DPROS, DCAPS, PSA, VOL, GLEASON, Intercept]
    FVecTest.makeByteVec(betaConsKey, "names, lower_bounds, upper_bounds\n AGE, -.5, .5\n RACE, -.5, .5\n DCAPS, -.4, .4\n DPROS, -.5, .5 \nPSA, -.5, .5\n VOL, -.5, .5\nGLEASON, -.5, .5");
    Frame betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);

    try {
      // H2O differs on intercept and race, same residual deviance though
      GLMParameters params = new GLMParameters();
      params._standardize = true;
      params._family = Family.binomial;
      params._beta_constraints = betaConstraints._key;
      params._response_column = "CAPSULE";
      params._ignored_columns = new String[]{"ID"};
      params._train = fr._key;
      params._objective_epsilon = 0;
      params._alpha = new double[]{1};
      params._lambda = new double[]{0.001607};
      params._obj_reg = 1.0/380;
      GLM glm = new GLM( params, modelKey);
      model = glm.trainModel().get();
      assertTrue(glm.isStopped());
//      Map<String, Double> coefs =  model.coefficients();
//      for (int i = 0; i < cfs1.length; ++i)
//        assertEquals(vals[i], coefs.get(cfs1[i]), 1e-1);
      ModelMetricsBinomialGLM val = (ModelMetricsBinomialGLM) model._output._training_metrics;
      assertEquals(512.2888, val._nullDev, 1e-1);
      // 388.4952716196743
      assertTrue(val._resDev <= 388.5);
      model.delete();
      params._lambda = new double[]{0};
      params._alpha = new double[]{0};
      FVecTest.makeByteVec(betaConsKey, "names, lower_bounds, upper_bounds\n RACE, -.5, .5\n DCAPS, -.4, .4\n DPROS, -.5, .5 \nPSA, -.5, .5\n VOL, -.5, .5");
      betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);
      glm = new GLM( params, modelKey);
      model = glm.trainModel().get();
      assertTrue(glm.isStopped());
      double[] beta = model.beta();
      System.out.println("beta = " + Arrays.toString(beta));
      fr.add("CAPSULE", fr.remove("CAPSULE"));
      fr.remove("ID").remove();
      DKV.put(fr._key, fr);
      // now check the ginfo
      DataInfo dinfo = new DataInfo(fr, null, 1, true, TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false, false, false);
      GLMGradientTask lt = new GLMBinomialGradientTask(null,dinfo,params,0,beta).doAll(dinfo._adaptedFrame);
      double [] grad = lt._gradient;
      String [] names = model.dinfo().coefNames();
      BufferedString tmpStr = new BufferedString();
      outer:
      for (int i = 0; i < names.length; ++i) {
        for (int j = 0; j < betaConstraints.numRows(); ++j) {
          if (betaConstraints.vec("names").atStr(tmpStr, j).toString().equals(names[i])) {
            if (Math.abs(beta[i] - betaConstraints.vec("lower_bounds").at(j)) < 1e-4 || Math.abs(beta[i] - betaConstraints.vec("upper_bounds").at(j)) < 1e-4) {
              continue outer;
            }
          }
        }
        assertEquals(0, grad[i], 1e-2);
      }
    } finally {
      fr.delete();
      betaConstraints.delete();
      if (model != null) model.delete();
    }
  }

  @Test
  public void testCoordinateDescent_airlines() {
    GLMModel model = null;

    Key parsed = Key.make("airlines_parsed");
    Key<GLMModel> modelKey = Key.make("airlines_model");

    Frame fr = parse_test_file(parsed, "smalldata/airlines/AirlinesTrain.csv.zip");

    try {
      // H2O differs on intercept and race, same residual deviance though
      GLMParameters params = new GLMParameters();
      params._standardize = true;
      params._family = Family.binomial;
      params._solver = Solver.COORDINATE_DESCENT_NAIVE;
      params._response_column = "IsDepDelayed";
      params._ignored_columns = new String[]{"IsDepDelayed_REC"};
      params._train = fr._key;
      GLM glm = new GLM( params, modelKey);
      model = glm.trainModel().get();
      assertTrue(glm.isStopped());
      System.out.println(model._output._training_metrics);

    } finally {
      fr.delete();
      if (model != null) model.delete();
    }
  }

  @Test
  public void testCoordinateDescent_airlines_CovUpdates() {
    GLMModel model = null;

    Key parsed = Key.make("airlines_parsed");
    Key<GLMModel> modelKey = Key.make("airlines_model");

    Frame fr = parse_test_file(parsed, "smalldata/airlines/AirlinesTrain.csv.zip");

    try {
      // H2O differs on intercept and race, same residual deviance though
      GLMParameters params = new GLMParameters();
      params._standardize = true;
      params._family = Family.binomial;
      params._solver = Solver.COORDINATE_DESCENT;
      params._response_column = "IsDepDelayed";
      params._ignored_columns = new String[]{"IsDepDelayed_REC"};
      params._train = fr._key;
      GLM glm = new GLM( params, modelKey);
      model = glm.trainModel().get();
      assertTrue(glm.isStopped());
      System.out.println(model._output._training_metrics);

    } finally {
      fr.delete();
      if (model != null) model.delete();
    }
  }

  @Test
  public void testCoordinateDescent_anomaly() {
    GLMModel model = null;
    Key parsed = Key.make("anomaly_parsed");
    Key<GLMModel> modelKey = Key.make("anomaly_model");

    Frame fr = parse_test_file(parsed, "smalldata/anomaly/ecg_discord_train.csv");

    try {
      // H2O differs on intercept and race, same residual deviance though
      GLMParameters params = new GLMParameters();
      params._standardize = true;
      params._family = Family.gaussian;
      params._solver = Solver.COORDINATE_DESCENT_NAIVE;
      params._response_column = "C1";
      params._train = fr._key;
      GLM glm = new GLM( params, modelKey);
      model = glm.trainModel().get();
      assertTrue(glm.isStopped());
      System.out.println(model._output._training_metrics);

    } finally {
      fr.delete();
      if (model != null) model.delete();
    }
  }


  @Test
  public void testCoordinateDescent_anomaly_CovUpdates() {
    GLMModel model = null;
    Key parsed = Key.make("anomaly_parsed");
    Key<GLMModel> modelKey = Key.make("anomaly_model");

    Frame fr = parse_test_file(parsed, "smalldata/anomaly/ecg_discord_train.csv");

    try {
      // H2O differs on intercept and race, same residual deviance though
      GLMParameters params = new GLMParameters();
      params._standardize = true;
      params._family = Family.gaussian;
      params._solver = Solver.COORDINATE_DESCENT;
      params._response_column = "C1";
      params._train = fr._key;
      GLM glm = new GLM( params, modelKey);
      model = glm.trainModel().get();
      assertTrue(glm.isStopped());
      System.out.println(model._output._training_metrics);

    } finally {
      fr.delete();
      if (model != null) model.delete();
    }
  }


  @Test
  public void testProximal() {
//    glmnet's result:
//    res2 <- glmnet(x=M,y=D$CAPSULE,lower.limits=-.5,upper.limits=.5,family='binomial')
//    res2$beta[,58]
//    AGE        RACE          DPROS       PSA         VOL         GLEASON
//    -0.00616326 -0.50000000  0.50000000  0.03628192 -0.01249324  0.50000000 //    res2$a0[100]
//    res2$a0[58]
//    s57
//    -4.155864
//    lambda = 0.001108, null dev =  512.2888, res dev = 379.7597
    Key parsed = Key.make("prostate_parsed");
    Key<GLMModel> modelKey = Key.make("prostate_model");
    GLMModel model = null;

    Frame fr = parse_test_file(parsed, "smalldata/logreg/prostate.csv");
    fr.remove("ID").remove();
    DKV.put(fr._key, fr);
    Key betaConsKey = Key.make("beta_constraints");

    FVecTest.makeByteVec(betaConsKey, "names, beta_given, rho\n AGE, 0.1, 1\n RACE, -0.1, 1 \n DPROS, 10, 1 \n DCAPS, -10, 1 \n PSA, 0, 1\n VOL, 0, 1\nGLEASON, 0, 1\n Intercept, 0, 0 \n");
    Frame betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);
    try {
      // H2O differs on intercept and race, same residual deviance though
      GLMParameters params = new GLMParameters();
      params._standardize = false;
      params._family = Family.binomial;
      params._beta_constraints = betaConstraints._key;
      params._response_column = "CAPSULE";
      params._ignored_columns = new String[]{"ID"};
      params._train = fr._key;
      params._alpha = new double[]{0};
      params._lambda = new double[]{0};
      params._obj_reg = 1.0/380;
      params._objective_epsilon = 0;
      GLM glm = new GLM( params, modelKey);
      model = glm.trainModel().get();
      double[] beta_1 = model.beta();
      params._solver = Solver.L_BFGS;
      params._max_iterations = 1000;
      glm = new GLM( params, modelKey);
      model = glm.trainModel().get();
      fr.add("CAPSULE", fr.remove("CAPSULE"));
      // now check the ginfo
      DataInfo dinfo = new DataInfo(fr, null, 1, true, TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false, false, false);
      GLMGradientTask lt = new GLMBinomialGradientTask(null,dinfo, params, 0, beta_1).doAll(dinfo._adaptedFrame);
      double[] grad = lt._gradient;
      for (int i = 0; i < beta_1.length; ++i)
        assertEquals(0, grad[i] + betaConstraints.vec("rho").at(i) * (beta_1[i] - betaConstraints.vec("beta_given").at(i)), 1e-4);
    } finally {
      betaConstraints.delete();
      fr.delete();
      if (model != null) model.delete();
    }
  }


//  // test categorical autoexpansions, run on airlines which has several categorical columns,
//  // once on explicitly expanded data, once on h2o autoexpanded and compare the results
//  @Test public void testSparseCategoricals() {
//    GLMModel model1 = null, model2 = null, model3 = null, model4 = null;
//
//    Frame frMM = parse_test_file("smalldata/glm_tets/train-2.csv");
//
////    Vec xy = frG.remove("xy");
//    frMM.remove("").remove();
//    frMM.add("IsDepDelayed", frMM.remove("IsDepDelayed"));
//    DKV.put(frMM._key,frMM);
//    Frame fr = parse_test_file("smalldata/airlines/AirlinesTrain.csv.zip"), res = null;
//    //  Distance + Origin + Dest + UniqueCarrier
//    String [] ignoredCols = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "DepTime","ArrTime","IsDepDelayed_REC"};
//    try{
//      Scope.enter();
//      GLMParameters params = new GLMParameters(Family.gaussian);
//      params._response_column = "IsDepDelayed";
//      params._ignored_columns = ignoredCols;
//      params._train = fr._key;
//      params._l2pen = new double[]{1e-5};
//      params._standardize = false;
//      model1 = new GLM(params,glmkey("airlines_cat_nostd")).trainModel().get();
//      Frame score1 = model1.score(fr);
//      ModelMetricsRegressionGLM mm = (ModelMetricsRegressionGLM) ModelMetrics.getFromDKV(model1, fr);
//      Assert.assertEquals(model1.validation().residual_deviance, mm._resDev, 1e-4);
//      System.out.println("NDOF = " + model1.validation().nullDOF() + ", numRows = " + score1.numRows());
//      Assert.assertEquals(model1.validation().residual_deviance, mm._MSE * score1.numRows(), 1e-4);
//      mm.remove();
//      res = model1.score(fr);
//      // Build a POJO, validate same results
//      Assert.assertTrue(model1.testJavaScoring(fr, res, 1e-15));
//
//      params._train = frMM._key;
//      params._ignored_columns = new String[]{"X"};
//      model2 = new GLM(params,glmkey("airlines_mm")).trainModel().get();
//      params._standardize = true;
//      params._train = frMM._key;
//      params._use_all_factor_levels = true;
//      // test the gram
//      DataInfo dinfo = new DataInfo(Key.make(),frMM, null, 1, true, DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true);
//      GLMIterationTask glmt = new GLMIterationTask(null,dinfo,1e-5,params,false,null,0,null, null).doAll(dinfo._adaptedFrame);
//      for(int i = 0; i < glmt._xy.length; ++i) {
//        for(int j = 0; j <= i; ++j ) {
//          assertEquals(frG.vec(j).at(i), glmt._gram.get(i, j), 1e-5);
//        }
//        assertEquals(xy.at(i), glmt._xy[i], 1e-5);
//      }
//      frG.delete();
//      xy.remove();
//      params._standardize = true;
//      params._family = Family.binomial;
//      params._link = Link.logit;
//      model3 = new GLM(params,glmkey("airlines_mm")).trainModel().get();
//      params._train = fr._key;
//      params._ignored_columns = ignoredCols;
//      model4 = new GLM(params,glmkey("airlines_mm")).trainModel().get();
//      assertEquals(model3.validation().null_deviance,model4.validation().nullDeviance(),1e-4);
//      assertEquals(model4.validation().residual_deviance, model3.validation().residualDeviance(), model3.validation().null_deviance * 1e-3);
//      HashMap<String, Double> coefs1 = model1.coefficients();
//      HashMap<String, Double> coefs2 = model2.coefficients();
//      GLMValidation val1 = model1.validation();
//      GLMValidation val2 = model2.validation();
//      // compare against each other
//      for(String s:coefs2.keySet()) {
//        String s1 = s;
//        if(s.startsWith("Origin"))
//          s1 = "Origin." + s.substring(6);
//        if(s.startsWith("Dest"))
//          s1 = "Dest." + s.substring(4);
//        if(s.startsWith("UniqueCarrier"))
//          s1 = "UniqueCarrier." + s.substring(13);
//        assertEquals("coeff " + s1 + " differs, " + coefs1.get(s1) + " != " + coefs2.get(s), coefs1.get(s1), coefs2.get(s),1e-4);
//        DKV.put(frMM._key,frMM); // update the frame in the KV after removing the vec!
//      }
//      assertEquals(val1.nullDeviance(), val2.nullDeviance(),1e-4);
//      assertEquals(val1.residualDeviance(), val2.residualDeviance(),1e-4);
//      assertEquals(val1._aic, val2._aic,1e-2);
//      // compare result against glmnet
//      assertEquals(5336.918,val1.residualDeviance(),1);
//      assertEquals(6051.613,val1.nullDeviance(),1);
//
//
//      // lbfgs
////      params._solver = Solver.L_BFGS;
////      params._train = fr._key;
////      params._lambda = new double[]{.3};
////      model3 = new GLM(params,glmkey("lbfgs_cat")).trainModel().get();
////      params._train = frMM._key;
////      model4 = new GLM(params,glmkey("lbfgs_mm")).trainModel().get();
////      HashMap<String, Double> coefs3 = model3.coefficients();
////      HashMap<String, Double> coefs4 = model4.coefficients();
////      // compare against each other
////      for(String s:coefs4.keySet()) {
////        String s1 = s;
////        if(s.startsWith("Origin"))
////          s1 = "Origin." + s.substring(6);
////        if(s.startsWith("Dest"))
////          s1 = "Dest." + s.substring(4);
////        if(s.startsWith("UniqueCarrier"))
////          s1 = "UniqueCarrier." + s.substring(13);
////        assertEquals("coeff " + s1 + " differs, " + coefs3.get(s1) + " != " + coefs4.get(s), coefs3.get(s1), coefs4.get(s),1e-4);
////      }
//
//    } finally {
//      fr.delete();
//      frMM.delete();
//      if(res != null)res.delete();
//      if(model1 != null)model1.delete();
//      if(model2 != null)model2.delete();
//      if(model3 != null)model3.delete();
//      if(model4 != null)model4.delete();
////      if(score != null)score.delete();
//      Scope.exit();
//    }
//  }

  /**
   * Test we get correct gram on dataset which contains categoricals and sparse and dense numbers
   */
  @Test
  public void testSparseGramComputation() {
    Random rnd = new Random(123456789l);
    double[] d0 = MemoryManager.malloc8d(1000);
    double[] d1 = MemoryManager.malloc8d(1000);
    double[] d2 = MemoryManager.malloc8d(1000);
    double[] d3 = MemoryManager.malloc8d(1000);
    double[] d4 = MemoryManager.malloc8d(1000);
    double[] d5 = MemoryManager.malloc8d(1000);
    double[] d6 = MemoryManager.malloc8d(1000);
    double[] d7 = MemoryManager.malloc8d(1000);
    double[] d8 = MemoryManager.malloc8d(1000);
    double[] d9 = MemoryManager.malloc8d(1000);

    long[] c1 = MemoryManager.malloc8(1000);
    long[] c2 = MemoryManager.malloc8(1000);
    String[] dom = new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"};
    for (int i = 0; i < d1.length; ++i) {
      c1[i] = rnd.nextInt(dom.length);
      c2[i] = rnd.nextInt(dom.length);
      d0[i] = rnd.nextDouble();
      d1[i] = rnd.nextDouble();
    }
    for (int i = 0; i < 30; ++i) {
      d2[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d3[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d4[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d5[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d6[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d7[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d8[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d9[rnd.nextInt(d2.length)] = 1;
    }
    Vec.VectorGroup vg_1 = Vec.VectorGroup.VG_LEN1;
    Vec v01 = Vec.makeVec(c1, dom, vg_1.addVec());
    Vec v02 = Vec.makeVec(c2, dom,vg_1.addVec());
    Vec v03 = Vec.makeVec(d0, vg_1.addVec());
    Vec v04 = Vec.makeVec(d1, vg_1.addVec());
    Vec v05 = Vec.makeVec(d2, vg_1.addVec());
    Vec v06 = Vec.makeVec(d3, vg_1.addVec());
    Vec v07 = Vec.makeVec(d4, vg_1.addVec());
    Vec v08 = Vec.makeVec(d5, vg_1.addVec());
    Vec v09 = Vec.makeVec(d6, vg_1.addVec());
    Vec v10 = Vec.makeVec(d7, vg_1.addVec());
    Vec v11 = Vec.makeVec(d8, vg_1.addVec());
    Vec v12 = Vec.makeVec(d9, vg_1.addVec());

    Frame f = new Frame(Key.<Frame>make("TestData"), null, new Vec[]{v01, v02, v03, v04, v05, v05, v06, v07, v08, v09, v10, v11, v12});
    DKV.put(f);
    DataInfo dinfo = new DataInfo(f, null, 1, true, DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true, false, false, false, false, false);
    GLMParameters params = new GLMParameters(Family.gaussian);
    //                              public  GLMIterationTask(Key jobKey, DataInfo dinfo, GLMWeightsFun glmw,double [] beta, double lambda) {
    final GLMIterationTask glmtSparse = new GLMIterationTask(null, dinfo, new GLMWeightsFun(params), null).setSparse(true).doAll(dinfo._adaptedFrame);
    final GLMIterationTask glmtDense = new GLMIterationTask(null, dinfo, new GLMWeightsFun(params), null).setSparse(false).doAll(dinfo._adaptedFrame);
    for (int i = 0; i < glmtDense._xy.length; ++i) {
      for (int j = 0; j <= i; ++j) {
        assertEquals(glmtDense._gram.get(i, j), glmtSparse._gram.get(i, j), 1e-8);
      }
      assertEquals(glmtDense._xy[i], glmtSparse._xy[i], 1e-8);
    }
    final double[] beta = MemoryManager.malloc8d(dinfo.fullN() + 1);
    // now do the same but weighted, use LSM solution as beta to generate meaningfull weights
    H2O.submitTask(new H2OCountedCompleter() {
      @Override
      public void compute2() {
        new GLM.GramSolver(glmtDense._gram, glmtDense._xy, true, 1e-5, 0, null, null, null, null).solve(null, beta);
        tryComplete();
      }
    }).join();
    final GLMIterationTask glmtSparse2 = new GLMIterationTask(null, dinfo, new GLMWeightsFun(params), beta).setSparse(true).doAll(dinfo._adaptedFrame);
    final GLMIterationTask glmtDense2 = new GLMIterationTask(null, dinfo, new GLMWeightsFun(params), beta).setSparse(false).doAll(dinfo._adaptedFrame);
    for (int i = 0; i < glmtDense2._xy.length; ++i) {
      for (int j = 0; j <= i; ++j) {
        assertEquals(glmtDense2._gram.get(i, j), glmtSparse2._gram.get(i, j), 1e-8);
      }
      assertEquals(glmtDense2._xy[i], glmtSparse2._xy[i], 1e-8);
    }
    dinfo.remove();
    f.delete();
  }

  // test categorical autoexpansions, run on airlines which has several categorical columns,
  // once on explicitly expanded data, once on h2o autoexpanded and compare the results
  @Test
  public void testAirlines() {
    GLMModel model1 = null, model2 = null, model3 = null, model4 = null;
    Frame frMM = parse_test_file(Key.make("AirlinesMM"), "smalldata/airlines/AirlinesTrainMM.csv.zip");
    Frame frG = parse_test_file(Key.make("gram"), "smalldata/airlines/gram_std.csv", true);
    Vec xy = frG.remove("xy");
    frMM.remove("C1").remove();
    Vec v;
    frMM.add("IsDepDelayed", (v = frMM.remove("IsDepDelayed")).makeCopy(null));
    v.remove();
    DKV.put(frMM._key, frMM);
    Frame fr = parse_test_file(Key.make("Airlines"), "smalldata/airlines/AirlinesTrain.csv.zip"), res = null;
    fr.add("IsDepDelayed",(v =fr.remove("IsDepDelayed")).makeCopy(null));
    v.remove();
    DKV.put(fr._key,fr);
    //  Distance + Origin + Dest + UniqueCarrier
    String[] ignoredCols = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "DepTime", "ArrTime", "IsDepDelayed_REC"};
    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.gaussian);
      params._response_column = "IsDepDelayed";
      params._ignored_columns = ignoredCols;
      params._train = fr._key;
      params._lambda = new double[]{0};
      params._alpha = new double[]{0};
      params._standardize = false;
      params._use_all_factor_levels = false;
      model1 = new GLM(params).trainModel().get();
      testScoring(model1,fr);
      Frame score1 = model1.score(fr);
      ModelMetricsRegressionGLM mm = (ModelMetricsRegressionGLM) ModelMetrics.getFromDKV(model1, fr);
      Assert.assertEquals(((ModelMetricsRegressionGLM) model1._output._training_metrics)._resDev, mm._resDev, 1e-4);
      Assert.assertEquals(((ModelMetricsRegressionGLM) model1._output._training_metrics)._resDev, mm._MSE * score1.numRows(), 1e-4);
      score1.delete();
      mm.remove();
      res = model1.score(fr);
      // Build a POJO, validate same results
      params._train = frMM._key;
      params._ignored_columns = new String[]{"X"};
      model2 = new GLM( params).trainModel().get();      HashMap<String, Double> coefs1 = model1.coefficients();
      testScoring(model2,frMM);
      HashMap<String, Double> coefs2 = model2.coefficients();
      boolean failed = false;
      // compare against each other
      for (String s : coefs2.keySet()) {
        String s1 = s;
        if (s.startsWith("Origin"))
          s1 = "Origin." + s.substring(6);
        if (s.startsWith("Dest"))
          s1 = "Dest." + s.substring(4);
        if (s.startsWith("UniqueCarrier"))
          s1 = "UniqueCarrier." + s.substring(13);
        if(Math.abs(coefs1.get(s1) - coefs2.get(s)) > 1e-4) {
          System.out.println("coeff " + s1 + " differs, " + coefs1.get(s1) + " != " + coefs2.get(s));
          failed = true;
        }
//        assertEquals("coeff " + s1 + " differs, " + coefs1.get(s1) + " != " + coefs2.get(s), coefs1.get(s1), coefs2.get(s), 1e-4);
      }
      assertFalse(failed);
      params._standardize = true;
      params._train = frMM._key;
      params._use_all_factor_levels = true;
      // test the gram
      DataInfo dinfo = new DataInfo(frMM, null, 1, true, DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true, false, false, false, false, false);
      GLMIterationTask glmt = new GLMIterationTask(null, dinfo, new GLMWeightsFun(params), null).doAll(dinfo._adaptedFrame);
      for(int i = 0; i < glmt._xy.length; ++i) {
        for(int j = 0; j <= i; ++j ) {
          assertEquals(frG.vec(j).at(i), glmt._gram.get(i, j), 1e-5);
        }
        assertEquals(xy.at(i), glmt._xy[i], 1e-5);
      }
      xy.remove();
      params = (GLMParameters) params.clone();
      params._standardize = false;
      params._family = Family.binomial;
      params._link = Link.logit;
      model3 = new GLM( params).trainModel().get();
      testScoring(model3,frMM);
      params._train = fr._key;
      params._ignored_columns = ignoredCols;
      model4 = new GLM( params).trainModel().get();
      testScoring(model4,fr);
      assertEquals(nullDeviance(model3), nullDeviance(model4), 1e-4);
      assertEquals(residualDeviance(model4), residualDeviance(model3), nullDeviance(model3) * 1e-3);

      assertEquals(nullDeviance(model1), nullDeviance(model2), 1e-4);
      assertEquals(residualDeviance(model1), residualDeviance(model2), 1e-4);
//      assertEquals(val1._aic, val2._aic,1e-2);
      // compare result against glmnet
      assertEquals(5336.918, residualDeviance(model1), 1);
      assertEquals(6051.613, nullDeviance(model2), 1);


      // lbfgs
//      params._solver = Solver.L_BFGS;
//      params._train = fr._key;
//      params._lambda = new double[]{.3};
//      model3 = new GLM(params,glmkey("lbfgs_cat")).trainModel().get();
//      params._train = frMM._key;
//      mdoel4 = new GLM(params,glmkey("lbfgs_mm")).trainModel().get();
//      HashMap<String, Double> coefs3 = model3.coefficients();
//      HashMap<String, Double> coefs4 = model4.coefficients();
//      // compare against each other
//      for(String s:coefs4.keySet()) {
//        String s1 = s;
//        if(s.startsWith("Origin"))
//          s1 = "Origin." + s.substring(6);
//        if(s.startsWith("Dest"))
//          s1 = "Dest." + s.substring(4);
//        if(s.startsWith("UniqueCarrier"))
//          s1 = "UniqueCarrier." + s.substring(13);
//        assertEquals("coeff " + s1 + " differs, " + coefs3.get(s1) + " != " + coefs4.get(s), coefs3.get(s1), coefs4.get(s),1e-4);
//      }

    } finally {
      fr.delete();
      frMM.delete();
      frG.delete();

      if (res != null) res.delete();
      if (model1 != null) model1.delete();
      if (model2 != null) model2.delete();
      if (model3 != null) model3.delete();
      if (model4 != null) model4.delete();
//      if(score != null)score.delete();
      Scope.exit();
    }
  }


  // test categorical autoexpansions, run on airlines which has several categorical columns,
  // once on explicitly expanded data, once on h2o autoexpanded and compare the results
  @Test
  public void test_COD_Airlines_SingleLambda() {
    GLMModel model1 = null;
    Frame fr = parse_test_file(Key.make("Airlines"), "smalldata/airlines/AirlinesTrain.csv.zip"); //  Distance + Origin + Dest + UniqueCarrier
    String[] ignoredCols = new String[]{"IsDepDelayed_REC"};
    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "IsDepDelayed";
      params._ignored_columns = ignoredCols;
      params._train = fr._key;
      params._valid = fr._key;
      params._lambda = new double[] {0.01};//null; //new double[]{0.02934};//{0.02934494}; // null;
      params._alpha = new double[]{1};
      params._standardize = false;
      params._solver = Solver.COORDINATE_DESCENT_NAIVE;
      params._lambda_search = true;
      params._nlambdas = 5;
      GLM glm = new GLM( params);
      model1 = glm.trainModel().get();
      double [] beta = model1.beta();
      double l1pen = ArrayUtils.l1norm(beta,true);
      double l2pen = ArrayUtils.l2norm2(beta,true);
      //System.out.println( " lambda min " + params._l2pen[params._l2pen.length-1] );
      //System.out.println( " lambda_max " + model1._lambda_max);
      //System.out.println(" intercept " + beta[beta.length-1]);
//      double objective = model1._output._training_metrics./model1._nobs +
//              params._l2pen[params._l2pen.length-1]*params._alpha[0]*l1pen + params._l2pen[params._l2pen.length-1]*(1-params._alpha[0])*l2pen/2  ;
//      System.out.println( " objective value " + objective);
//      assertEquals(0.670921, objective,1e-4);
    } finally {
      fr.delete();
      if (model1 != null) model1.delete();
    }
  }


  @Test
  public void test_COD_Airlines_SingleLambda_CovUpdates() {
    GLMModel model1 = null;
    Frame fr = parse_test_file(Key.make("Airlines"), "smalldata/airlines/AirlinesTrain.csv.zip"); //  Distance + Origin + Dest + UniqueCarrier
    String[] ignoredCols = new String[]{"IsDepDelayed_REC"};
    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "IsDepDelayed";
      params._ignored_columns = ignoredCols;
      params._train = fr._key;
      params._valid = fr._key;
      params._lambda = new double[] {0.01};//null; //new double[]{0.02934};//{0.02934494}; // null;
      params._alpha = new double[]{1};
      params._standardize = false;
      params._solver = Solver.COORDINATE_DESCENT;
      params._lambda_search = true;
      GLM glm = new GLM( params);
      model1 = glm.trainModel().get();
      double [] beta = model1.beta();
      double l1pen = ArrayUtils.l1norm(beta,true);
      double l2pen = ArrayUtils.l2norm2(beta,true);
//      double objective = job.likelihood()/model1._nobs +
//              params._l2pen[params._l2pen.length-1]*params._alpha[0]*l1pen + params._l2pen[params._l2pen.length-1]*(1-params._alpha[0])*l2pen/2  ;
//      System.out.println( " objective value " + objective);
//      assertEquals(0.670921, objective,1e-2);
    } finally {
      fr.delete();
      if (model1 != null) model1.delete();
    }
  }


  @Test
  public void test_COD_Airlines_LambdaSearch() {
    GLMModel model1 = null;
    Frame fr = parse_test_file(Key.make("Airlines"), "smalldata/airlines/AirlinesTrain.csv.zip"); //  Distance + Origin + Dest + UniqueCarrier
    String[] ignoredCols = new String[]{"IsDepDelayed_REC"};
    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "IsDepDelayed";
      params._ignored_columns = ignoredCols;
      params._train = fr._key;
      params._valid = fr._key;
      params._lambda = null; // new double [] {0.25};
      params._alpha = new double[]{1};
      params._standardize = false;
      params._solver = Solver.COORDINATE_DESCENT_NAIVE;//IRLSM
      params._lambda_search = true;
      params._nlambdas = 5;
      GLM glm = new GLM( params);
      model1 = glm.trainModel().get();
      GLMModel.Submodel sm = model1._output._submodels[model1._output._submodels.length-1];
      double [] beta = sm.beta;
      System.out.println("lambda " + sm.lambda_value);
      double l1pen = ArrayUtils.l1norm(beta,true);
      double l2pen = ArrayUtils.l2norm2(beta,true);
//      double objective = job.likelihood()/model1._nobs + // gives likelihood of the last lambda
//              params._l2pen[params._l2pen.length-1]*params._alpha[0]*l1pen + params._l2pen[params._l2pen.length-1]*(1-params._alpha[0])*l2pen/2  ;
//      assertEquals(0.65689, objective,1e-4);
    } finally {
      fr.delete();
      if (model1 != null) model1.delete();
    }
  }


  @Test
  public void test_COD_Airlines_LambdaSearch_CovUpdates() {
    GLMModel model1 = null;
    Frame fr = parse_test_file(Key.make("Airlines"), "smalldata/airlines/AirlinesTrain.csv.zip"); //  Distance + Origin + Dest + UniqueCarrier
    String[] ignoredCols = new String[]{"IsDepDelayed_REC"};
    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "IsDepDelayed";
      params._ignored_columns = ignoredCols;
      params._train = fr._key;
      params._valid = fr._key;
      params._lambda = null; // new double [] {0.25};
      params._alpha = new double[]{1};
      params._standardize = false;
      params._solver = Solver.COORDINATE_DESCENT;
      params._lambda_search = true;
      params._nlambdas = 5;
      GLM glm = new GLM( params);
      model1 = glm.trainModel().get();
      GLMModel.Submodel sm = model1._output._submodels[model1._output._submodels.length-1];
      double [] beta = sm.beta;
      System.out.println("lambda " + sm.lambda_value);
      double l1pen = ArrayUtils.l1norm(beta,true);
      double l2pen = ArrayUtils.l2norm2(beta,true);
//      double objective = job.likelihood()/model1._nobs + // gives likelihood of the last lambda
//              params._l2pen[params._l2pen.length-1]*params._alpha[0]*l1pen + params._l2pen[params._l2pen.length-1]*(1-params._alpha[0])*l2pen/2  ;
//      assertEquals(0.65689, objective,1e-4);
    } finally {
      fr.delete();
      if (model1 != null) model1.delete();
    }
  }


  public static double residualDeviance(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._resDev;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._resDev;
    }
  }
  public static double residualDevianceTest(GLMModel m) {
    if(m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM)m._output._validation_metrics;
      return metrics._resDev;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM)m._output._validation_metrics;
      return metrics._resDev;
    }
  }
  public static double nullDevianceTest(GLMModel m) {
    if(m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM)m._output._validation_metrics;
      return metrics._nullDev;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM)m._output._validation_metrics;
      return metrics._nullDev;
    }
  }
  public static double aic(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._AIC;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._AIC;
    }
  }

  public static double nullDOF(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._nullDegressOfFreedom;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._nullDegressOfFreedom;
    }
  }

  public static double resDOF(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._residualDegressOfFreedom;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._residualDegressOfFreedom;
    }
  }

  public static double auc(GLMModel m) {
    ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
    return metrics.auc_obj()._auc;
  }
  public static double logloss(GLMModel m) {
    ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
    return metrics._logloss;
  }

  public static double mse(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._MSE;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._MSE;
    }
  }

  public static double nullDeviance(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._nullDev;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._nullDev;
    }
  }

  // test class
  private static final class GLMIterationTaskTest extends GLMIterationTask {
    final GLMModel _m;
    GLMMetricBuilder _val2;

    public GLMIterationTaskTest(Key jobKey, DataInfo dinfo, double lambda, GLMParameters glm, boolean validate, double[] beta, double ymu, GLMModel m) {
      // null, dinfo, new GLMWeightsFun(params), beta, 1e-5
      super(jobKey, dinfo, new GLMWeightsFun(glm), beta);
      _m = m;
    }

    public void map(Chunk[] chks) {
      super.map(chks);

      _val2 = (GLMMetricBuilder) _m.makeMetricBuilder(chks[chks.length - 1].vec().domain());
      double[] ds = new double[3];

      float[] actual = new float[1];
      for (int i = 0; i < chks[0]._len; ++i) {
        _m.score0(chks, i, null, ds);
        actual[0] = (float) chks[chks.length - 1].atd(i);
        _val2.perRow(ds, actual, _m);
      }
    }

    public void reduce(GLMIterationTask gmt) {
      super.reduce(gmt);
      GLMIterationTaskTest g = (GLMIterationTaskTest) gmt;
      _val2.reduce(g._val2);
    }
  }


  /**
   * Simple test for binomial family (no regularization, test both lsm solvers).
   * Runs the classical prostate, using dataset with race replaced by categoricals (probably as it's supposed to be?), in any case,
   * it gets to test correct processing of categoricals.
   *
   * Compare against the results from standard R glm implementation.
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Test public void testProstate() throws InterruptedException, ExecutionException {
    GLMModel model = null, model2 = null, model3 = null, model4 = null;
    Frame fr = parse_test_file("smalldata/glm_test/prostate_cat_replaced.csv");
    try{
      Scope.enter();
      // R results
//      Coefficients:
//        (Intercept)           ID          AGE       RACER2       RACER3        DPROS        DCAPS          PSA          VOL      GLEASON
//          -8.894088     0.001588    -0.009589     0.231777    -0.459937     0.556231     0.556395     0.027854    -0.011355     1.010179
      String [] cfs1 = new String [] {"Intercept","AGE", "RACE.R2","RACE.R3", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"};
      double [] vals = new double [] {-8.14867, -0.01368, 0.32337, -0.38028, 0.55964, 0.49548, 0.02794, -0.01104, 0.97704};
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "CAPSULE";
      params._ignored_columns = new String[]{"ID"};
      params._train = fr._key;
      params._lambda = new double[]{0};
      params._standardize = false;
//      params._missing_values_handling = MissingValuesHandling.Skip;
      GLM glm = new GLM(params);
      model = glm.trainModel().get();
      HashMap<String, Double> coefs = model.coefficients();
      System.out.println(coefs);
      for(int i = 0; i < cfs1.length; ++i)
        assertEquals(vals[i], coefs.get(cfs1[i]),1e-4);
      assertEquals(512.3, nullDeviance(model),1e-1);
      assertEquals(378.3, residualDeviance(model),1e-1);
      assertEquals(371,   resDOF(model),0);
      assertEquals(396.3, aic(model),1e-1);
      testScoring(model,fr);
      // test scoring
      model.score(fr).delete();
      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model,fr);
      hex.AUC2 adata = mm._auc;
      assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
      assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
      assertEquals(((ModelMetricsBinomialGLM)model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)mm)._resDev, 1e-8);
      model.score(fr).delete();
      mm = hex.ModelMetricsBinomial.getFromDKV(model,fr);
      assertEquals(model._output._training_metrics.auc_obj()._auc, adata._auc, 1e-8);
      assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
      assertEquals(((ModelMetricsBinomialGLM)model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)mm)._resDev, 1e-8);
      double prior = 1e-5;
      params._prior = prior;
      // test the same data and model with prior, should get the same model except for the intercept
      glm = new GLM(params);
      model2 = glm.trainModel().get();

      for(int i = 0; i < model2.beta().length-1; ++i)
        assertEquals(model.beta()[i], model2.beta()[i], 1e-8);
      assertEquals(model.beta()[model.beta().length-1] -Math.log(model._ymu[0] * (1-prior)/(prior * (1-model._ymu[0]))),model2.beta()[model.beta().length-1],1e-10);

      // run with lambda search, check the final submodel
      params._lambda_search = true;
      params._lambda = null;
      params._alpha = new double[]{0};
      params._prior = -1;
      params._obj_reg = -1;
      params._max_iterations = 500;
      params._objective_epsilon = 1e-6;
      // test the same data and model with prior, should get the same model except for the intercept
      glm = new GLM(params);
      model3 = glm.trainModel().get();
      double lambda =  model3._output._submodels[model3._output._best_lambda_idx].lambda_value;
      params._lambda_search = false;
      params._lambda = new double[]{lambda};
      ModelMetrics mm3 = ModelMetrics.getFromDKV(model3,fr);
      assertEquals("mse don't match, " + model3._output._training_metrics._MSE + " != " + mm3._MSE,model3._output._training_metrics._MSE,mm3._MSE,1e-8);
      assertEquals("res-devs don't match, " + ((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev + " != " + ((ModelMetricsBinomialGLM)mm3)._resDev,((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)mm3)._resDev,1e-4);
      fr.add("CAPSULE", fr.remove("CAPSULE"));
      fr.remove("ID").remove();
      DKV.put(fr._key,fr);
      DataInfo dinfo = new DataInfo(fr, null, 1, true, TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false, false, false);
      model3.score(fr).delete();
      mm3 = ModelMetrics.getFromDKV(model3,fr);
      assertEquals("mse don't match, " + model3._output._training_metrics._MSE + " != " + mm3._MSE,model3._output._training_metrics._MSE,mm3._MSE,1e-8);
      assertEquals("res-devs don't match, " + ((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev + " != " + ((ModelMetricsBinomialGLM)mm3)._resDev,((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)mm3)._resDev,1e-4);
      // test the same data and model with prior, should get the same model except for the intercept
      glm = new GLM(params);
      model4 = glm.trainModel().get();
      assertEquals("mse don't match, " + model3._output._training_metrics._MSE + " != " + model4._output._training_metrics._MSE,model3._output._training_metrics._MSE,model4._output._training_metrics._MSE,1e-6);
      assertEquals("res-devs don't match, " + ((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev + " != " + ((ModelMetricsBinomialGLM)model4._output._training_metrics)._resDev,((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)model4._output._training_metrics)._resDev,1e-4);
      model4.score(fr).delete();
      ModelMetrics mm4 = ModelMetrics.getFromDKV(model4,fr);
      assertEquals("mse don't match, " + mm3._MSE + " != " + mm4._MSE,mm3._MSE,mm4._MSE,1e-6);
      assertEquals("res-devs don't match, " + ((ModelMetricsBinomialGLM)mm3)._resDev + " != " + ((ModelMetricsBinomialGLM)mm4)._resDev,((ModelMetricsBinomialGLM)mm3)._resDev, ((ModelMetricsBinomialGLM)mm4)._resDev,1e-4);
//      GLMValidation val2 = new GLMValidationTsk(params,model._ymu,rank(model.beta())).doAll(new Vec[]{fr.vec("CAPSULE"),score.vec("1")})._val;
//      assertEquals(val.residualDeviance(),val2.residualDeviance(),1e-6);
//      assertEquals(val.nullDeviance(),val2.nullDeviance(),1e-6);
    } finally {
      fr.delete();
      if(model != null)model.delete();
      if(model2 != null)model2.delete();
      if(model3 != null)model3.delete();
      if(model4 != null)model4.delete();
      Scope.exit();
    }
  }

  @Test public void testSynthetic() throws Exception {
    GLMModel model = null;
    Frame fr = parse_test_file("smalldata/glm_test/glm_test2.csv");
    Frame score = null;
    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "response";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"ID"};
      params._train = fr._key;
      params._lambda = new double[]{0};
      params._standardize = false;
      params._max_iterations = 20;
      GLM glm = new GLM( params);
      model = glm.trainModel().get();
      double [] beta = model.beta();
      System.out.println("beta = " + Arrays.toString(beta));
      assertEquals(auc(model), 1, 1e-4);
      score = model.score(fr);

      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model,fr);

      hex.AUC2 adata = mm._auc;
      assertEquals(auc(model), adata._auc, 1e-2);
    } finally {
      fr.remove();
      if(model != null)model.delete();
      if(score != null)score.delete();
      Scope.exit();
    }
  }

  @Test //PUBDEV-1839
  public void testCitibikeReproPUBDEV1839() throws Exception {
    GLMModel model = null;
    Frame tfr = parse_test_file("smalldata/jira/pubdev_1839_repro_train.csv");
    Frame vfr = parse_test_file("smalldata/jira/pubdev_1839_repro_test.csv");

    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.poisson);
      params._response_column = "bikes";
      params._train = tfr._key;
      params._valid = vfr._key;
      GLM glm = new GLM(params);
      model = glm.trainModel().get();
      testScoring(model,vfr);
    } finally {
      tfr.remove();
      vfr.remove();
      if(model != null)model.delete();
      Scope.exit();
    }
  }

  @Test
  public void testCitibikeReproPUBDEV1953() throws Exception {
    GLMModel model = null;
    Frame tfr = parse_test_file("smalldata/glm_test/citibike_small_train.csv");
    Frame vfr = parse_test_file("smalldata/glm_test/citibike_small_test.csv");

    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.poisson);
      params._response_column = "bikes";
      params._train = tfr._key;
      params._valid = vfr._key;
      params._family = Family.poisson;
      GLM glm = new GLM( params);
      model = glm.trainModel().get();
      testScoring(model,vfr);
    } finally {
      tfr.remove();
      vfr.remove();
      if(model != null)model.delete();
      Scope.exit();
    }
  }

  /**
   * Test strong rules on arcene datasets (10k predictors, 100 rows).
   * Should be able to obtain good model (~100 predictors, ~1 explained deviance) with up to 250 active predictors.
   * Scaled down (higher lambda min, fewer lambdas) to run at reasonable speed (whole test takes 20s on my laptop).
   *
   * Test runs glm with gaussian on arcene dataset and verifies it gets all lambda while limiting maximum actove predictors to reasonably small number.
   * Compares the objective value to expected one.
   */
  @Test public void testArcene() throws InterruptedException, ExecutionException{
    Key parsed = Key.make("arcene_parsed");
    Key<GLMModel> modelKey = Key.make("arcene_model");
    GLMModel model = null;
    Frame fr = parse_test_file(parsed, "smalldata/glm_test/arcene.csv");
    try{
      Scope.enter();
      // test LBFGS with l1 pen
      GLMParameters params = new GLMParameters(Family.gaussian);
      // params._response = 0;
      params._lambda = null;
      params._response_column = fr._names[0];
      params._train = parsed;
      params._lambda_search = true;
      params._nlambdas = 35;
      params._lambda_min_ratio = 0.18;
      params._max_iterations = 100000;
      params._max_active_predictors = 215;
      params._alpha = new double[]{1};
      for(Solver s: new Solver[]{ Solver.IRLSM, Solver.COORDINATE_DESCENT}){//Solver.COORDINATE_DESCENT,}) { // LBFGS lambda-search is too slow now
        params._solver = s;
        GLM glm = new GLM( params, modelKey);
        glm.trainModel().get();
        model = DKV.get(modelKey).get();
        // assert on that we got all submodels (if strong rules work, we should be able to get the results with this many active predictors)
        assertEquals(params._nlambdas, model._output._submodels.length);
        System.out.println(model._output._training_metrics);
        // assert on the quality of the result, technically should compare objective value, but this should be good enough for now
      }
      model.delete();
      // test behavior when we can not fit within the active cols limit (should just bail out early and give us whatever it got)
      params = new GLMParameters(Family.gaussian);
      // params._response = 0;
      params._lambda = null;
      params._response_column = fr._names[0];
      params._train = parsed;
      params._lambda_search = true;
      params._nlambdas = 35;
      params._lambda_min_ratio = 0.18;
      params._max_active_predictors = 20;
      params._alpha = new double[]{1};
      GLM glm = new GLM(params);
      glm.trainModel().get();
      model = DKV.get(glm.dest()).get();
      testScoring(model,fr);
      assertTrue(model._output._submodels.length > 3);
      assertTrue(residualDeviance(model) <= 93);
      model.delete();
      model = null;
    } finally {
      fr.delete();
      if(model != null)model.delete();
      Scope.exit();
    }
  }

  /** Test large GLM POJO model generation.
   *  Make a 10K predictor model, emit, javac, and score with it.
   */
  @Test public void testBigPOJO() {
    GLMModel model = null;
    Frame fr = parse_test_file(Key.make("arcene_parsed"), "smalldata/glm_test/arcene.csv"), res=null;
    try{
      Scope.enter();
      // test LBFGS with l1 pen
      GLMParameters params = new GLMParameters(Family.gaussian);
      // params._response = 0;
      params._lambda = null;
      params._response_column = fr._names[0];
      params._train = fr._key;
      params._max_active_predictors = 100000;
      params._alpha = new double[]{0};
      params._solver = Solver.L_BFGS;
      GLM glm = new GLM(params);
      model = glm.trainModel().get();
      res = model.score(fr);
      model.testJavaScoring(fr,res,0.0);
    } finally {
      fr.delete();
      if(model != null) model.delete();
      if( res != null ) res.delete();
      Scope.exit();
    }
  }

  @Test public void testAbalone() {
    Scope.enter();
    GLMModel model = null;
    try {
      Frame fr = parse_test_file("smalldata/glm_test/Abalone.gz");
      Scope.track(fr);
      GLMParameters params = new GLMParameters(Family.gaussian);
      params._train = fr._key;
      params._response_column = fr._names[8];
      params._alpha = new double[]{1.0};
      params._lambda_search = true;
      GLM glm = new GLM(params);
      model = glm.trainModel().get();
      testScoring(model,fr);
    } finally {
      if( model != null ) model.delete();
      Scope.exit();
    }
  }

  @Test
  public void testDeviances() {
    for (Family fam : Family.values()) {
      Frame tfr = null;
      Frame res = null;
      Frame preds = null;
      GLMModel gbm = null;

      try {
        tfr = parse_test_file("./smalldata/gbm_test/BostonHousing.csv");
        GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
        parms._train = tfr._key;
        String resp = tfr.lastVecName();
        if (fam==Family.binomial || fam==Family.multinomial) {
          resp = fam==Family.multinomial?"rad":"chas";
          Vec v = tfr.remove(resp);
          tfr.add(resp, v.toCategoricalVec());
          v.remove();
          DKV.put(tfr);
        }
        parms._response_column = resp;
        parms._family = fam;

        gbm = new GLM(parms).trainModel().get();
        preds = gbm.score(tfr);

        res = gbm.computeDeviances(tfr,preds,"myDeviances");
        double meanDeviances = res.anyVec().mean();
        if (gbm._output.nclasses()==2)
          Assert.assertEquals(meanDeviances,((ModelMetricsBinomial) gbm._output._training_metrics)._logloss,1e-6*Math.abs(meanDeviances));
        else if (gbm._output.nclasses()>2)
          Assert.assertEquals(meanDeviances,((ModelMetricsMultinomial) gbm._output._training_metrics)._logloss,1e-6*Math.abs(meanDeviances));
        else
          Assert.assertEquals(meanDeviances,((ModelMetricsRegression) gbm._output._training_metrics)._mean_residual_deviance,1e-6*Math.abs(meanDeviances));

      } finally {
        if (tfr != null) tfr.delete();
        if (res != null) res.delete();
        if (preds != null) preds.delete();
        if (gbm != null) gbm.delete();
      }
    }
  }
}
