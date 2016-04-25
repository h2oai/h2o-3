package hex.pam;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.FVecTest;
import water.fvec.Frame;
import water.parser.ParseDataset;
import static org.junit.Assert.assertTrue;

public class PAMTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testBUILDPhase1() {
    PAMModel kmm = null;
    Key raw = Key.make("1D_pam_data_raw");
    Key parsed = Key.make("1D_pam_data_parsed");
    Frame tf = null;
    try {
      FVecTest.makeByteVec(raw, "x\n0\n1\n2\n10\n11\n12\n20\n21\n22");
      tf = ParseDataset.parse(parsed, raw);

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = tf._key;
      parms._k = 3;
      parms._do_swap = false;

      kmm = new PAM(parms).trainModel().get();

      assertTrue(kmm._output._medoid_rows.length == 3);
      assertTrue(contains(kmm._output._medoid_rows,1)); // row 1 = 1
      assertTrue(contains(kmm._output._medoid_rows,4)); // row 4 = 11
      assertTrue(contains(kmm._output._medoid_rows,7)); // row 7 = 21
    } finally {
      if( tf  != null ) tf.delete();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testBUILDPhase2() {
    PAMModel kmm = null;
    Key raw = Key.make("1D_pam_data_raw");
    Key parsed = Key.make("1D_pam_data_parsed");
    Frame tf = null;
    try {
      FVecTest.makeByteVec(raw, "x\n0\n1\n2\n51\n100\n101\n102");
      tf = ParseDataset.parse(parsed, raw);

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = tf._key;
      parms._k = 2;
      parms._do_swap = false;

      kmm = new PAM(parms).trainModel().get();

      assertTrue(kmm._output._medoid_rows.length == 2);
      assertTrue(contains(kmm._output._medoid_rows,3));
      assertTrue(contains(kmm._output._medoid_rows,5));
    } finally {
      if( tf  != null ) tf.delete();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testBUILDPhase1Euclidean() {
    PAMModel kmm = null;
    Key raw = Key.make("1D_pam_data_raw");
    Key parsed = Key.make("1D_pam_data_parsed");
    Frame tf = null;
    try {
      FVecTest.makeByteVec(raw, "x\n0\n1\n2\n51\n100\n101\n102");
      tf = ParseDataset.parse(parsed, raw);

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = tf._key;
      parms._k = 2;
      parms._dissimilarity_measure = PAM.DissimilarityMeasure.EUCLIDEAN;
      parms._do_swap = false;

      kmm = new PAM(parms).trainModel().get();

      assertTrue(kmm._output._medoid_rows.length == 2);
      assertTrue(contains(kmm._output._medoid_rows,3));
      assertTrue(contains(kmm._output._medoid_rows,5));
    } finally {
      if( tf  != null ) tf.delete();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testBUILDPhaseIris() {
    PAMModel kmm = null;
    Frame tf = null;
    try {
      tf = parse_test_file(Key.make("iris.hex"),"smalldata/iris/iris.csv");

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = tf._key;
      parms._k = 3;
      parms._do_swap = false;

      kmm = new PAM(parms).trainModel().get();

      assertTrue(kmm._output._medoid_rows.length == 3);
      assertTrue(contains(kmm._output._medoid_rows,7));
      assertTrue(contains(kmm._output._medoid_rows,95));
      assertTrue(contains(kmm._output._medoid_rows,116));

      double[] distances = {0.3,0.6,0.7,0.7,0.3,1.3,0.6,0.0,1.2,0.5,0.7,0.3,0.8,1.6,1.7,1.9,1.3,0.4,1.4,0.6,0.6,0.6,1.1,
              0.7,0.6,0.5,0.3,0.3,0.3,0.6,0.6,0.6,1.0,1.4,0.5,0.5,0.8,0.5,1.2,0.1,0.4,1.9,1.0,0.6,1.1,0.8,0.6,0.7,0.6,
              0.2,2.2,1.5,2.3,1.2,1.7,0.6,1.8,2.5,1.5,1.3,2.6,0.5,1.5,1.2,0.9,1.5,0.7,0.7,1.9,1.0,1.6,0.9,2.1,1.1,1.0,
              1.3,2.1,1.8,1.0,1.3,1.3,1.5,0.7,1.9,0.9,1.4,1.9,1.6,0.3,1.0,0.8,1.0,0.7,2.5,0.5,0.0,0.2,0.8,2.4,0.4,1.7,
              1.5,1.3,0.4,0.7,2.5,3.1,1.7,1.0,2.6,0.8,0.7,0.6,2.0,1.9,1.0,0.0,3.6,3.5,2.1,1.3,1.9,2.8,1.1,1.0,1.4,1.2,
              1.0,0.7,1.2,1.8,3.3,0.8,1.1,1.3,2.3,1.3,0.2,1.2,0.9,1.0,1.4,1.5,1.4,1.4,1.0,1.3,0.5,1.3,1.0};
    } finally {
      if( tf  != null ) tf.delete();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testBUILDPhaseProstate() {
    PAMModel kmm = null;
    Frame tf = null;
    try {
      tf = parse_test_file(Key.make("prostate.hex"),"smalldata/prostate/prostate.csv");

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = tf._key;
      parms._k = 8;
      parms._do_swap = false;

      kmm = new PAM(parms).trainModel().get();

      assertTrue(kmm._output._medoid_rows.length == 8);
      assertTrue(contains(kmm._output._medoid_rows,17));
      assertTrue(contains(kmm._output._medoid_rows,65));
      assertTrue(contains(kmm._output._medoid_rows,125));
      assertTrue(contains(kmm._output._medoid_rows,175));
      assertTrue(contains(kmm._output._medoid_rows,204));
      assertTrue(contains(kmm._output._medoid_rows,240));
      assertTrue(contains(kmm._output._medoid_rows,305));
      assertTrue(contains(kmm._output._medoid_rows,357));

      double[] distances = {36,37.9,32.1,79.8,69.8,31.5,60.1,104.5,26.5,36.2,29.2,42.4,46.9,63.2,25.7,16.8,19.9,0,32,
              30.7,30.3,86.3,38.8,57.2,122.7,34,28,16.6,43.3,41,44.3,32.6,47.2,40.3,37.4,33.9,49.4,60.4,70.4,45.5,45.3,
              39.4,33.3,35.5,129.9,71.4,84.1,40.8,38.5,39.5,35.9,176.8,58.9,73.8,34.1,40.7,37.2,22.5,39.3,53,120.1,46.8,
              30.7,13.2,28.9,0,14.3,21.3,22.3,43.2,20.5,36,21.6,39.2,14.2,77.5,48.3,24.9,23.9,72,34.9,32.3,47.7,64.2,
              47.3,39.1,116.5,132.2,70.1,42.8,68.6,33.8,37.2,75.8,69.6,50.7,38.4,45.8,55.3,43.6,42.7,56.7,63.2,120.3,
              47.1,60.3,47,67.4,28.8,43.2,44.2,49.2,31.5,75.8,93,66,64.3,37.4,52.6,26.6,41.4,49.7,28.9,28.5,23.1,0,10.2,
              33.7,38.6,27.7,41,35.4,30.8,33.7,41.6,16.6,36,114.9,121,36.4,40.9,75.9,73.6,67.7,44.2,44,90.6,54.4,53,47,
              46.5,29.5,31.7,50.5,51.71,106.1,57.9,46.6,77.1,45,49.2,38.5,55.3,54.4,31.6,34.9,40.3,42.6,30.7,68.5,29.7,
              26.8,30,36.9,11.5,0,33.5,28.2,35,42.7,42.1,64.9,48.3,39,42,52.3,101.1,33.4,35.1,23.8,19,84.7,53.7,28.61,
              24.7,50.3,18,43.6,18.1,34.9,11.2,23.2,10.1,17.3,0,51.2,61,40.9,28.7,19.5,45.1,16.4,53.8,38.9,35.3,51.6,
              66.9,52.7,33.4,39.4,29.6,70.4,47.4,31.9,38.71,31.7,28.6,31.8,32.2,39.6,40.2,47.8,33.6,144.9,34.3,29,19.8,
              31.2,12.3,14.1,0,14.7,49.7,26.3,26,27.4,31.1,133.3,39.7,54.2,38.4,46.2,47.7,41.6,78.6,56.1,45.2,38.7,48.7,
              42.9,53.3,46.3,45.6,39.5,34.8,44.1,37.7,65.6,47.9,52.3,50.1,57,74.2,52.9,45.5,60.1,51.7,47.8,52.3,44.6,
              53.8,42,39.7,46.1,42.8,74.9,49,44.6,38.1,38.4,29.6,33.1,34,42.1,29.3,26.2,36.6,42.8,33.6,29.5,80.7,21.5,
              150.7,16.2,31.7,0,23.5,12.5,73.3,32.8,40.2,30.1,32.6,34.7,55.7,41.7,51.6,25.5,80.3,31.1,25.8,35.5,35.2,
              49.8,90.5,37.8,47.6,40.6,37.1,63.2,48.1,49.48,39.8,55.6,46.5,45.8,53.6,104.7,33.9,69.3,47.2,37.2,30.7,
              24.9,106.9,34.6,36,42.2,52.9,43.4,35.3,30.4,39.9,38.8,94.2,25.3,36.2,0,32.4,36.3,20.4,31.7,73.4,32.5,34.5,
              23.9,41.4,88.3,34.8,28.4,46.9,34.7,37.3,166.9,52.1,50.1,33.2,66.7,43.6,30.1};
    } finally {
      if( tf  != null ) tf.delete();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testBUILDPhaseProstateEuclidean() {
    PAMModel kmm = null;
    Frame tf = null;
    try {
      tf = parse_test_file(Key.make("prostate.hex"),"smalldata/prostate/prostate.csv");

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = tf._key;
      parms._k = 8;
      parms._dissimilarity_measure = PAM.DissimilarityMeasure.EUCLIDEAN;
      parms._do_swap = false;

      kmm = new PAM(parms).trainModel().get();

      assertTrue(kmm._output._medoid_rows.length == 8);
      assertTrue(contains(kmm._output._medoid_rows,17));
      assertTrue(contains(kmm._output._medoid_rows,54));
      assertTrue(contains(kmm._output._medoid_rows,125));
      assertTrue(contains(kmm._output._medoid_rows,193));
      assertTrue(contains(kmm._output._medoid_rows,239));
      assertTrue(contains(kmm._output._medoid_rows,264));
      assertTrue(contains(kmm._output._medoid_rows,320));
      assertTrue(contains(kmm._output._medoid_rows,357));
    } finally {
      if( tf  != null ) tf.delete();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void test1() {
    PAMModel kmm = null;
    Key raw = Key.make("1D_pam_data_raw");
    Key parsed = Key.make("1D_pam_data_parsed");
    Frame fr = null;
    try {
      FVecTest.makeByteVec(raw, "x\n1\n10\n100");
      fr = ParseDataset.parse(parsed, raw);

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = fr._key;
      parms._k = 1;

      kmm = new PAM(parms).trainModel().get();
      assertTrue(1==kmm._output._medoid_rows[0]);
      assertTrue(10==kmm._output._medoids[0][0]);

    } finally {
      if( fr  != null ) fr.delete();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void test2() {
    PAMModel kmm = null;
    Key raw = Key.make("1D_pam_data_raw");
    Key parsed = Key.make("1D_pam_data_parsed");
    Frame fr = null;
    try {
      FVecTest.makeByteVec(raw, "x\n0\n1\n2\n10\n11\n12\n20\n21\n22");
      fr = ParseDataset.parse(parsed, raw);

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = fr._key;
      parms._k = 3;

      kmm = new PAM(parms).trainModel().get();
      assertTrue(contains(kmm._output._medoid_rows,4));
      assertTrue(contains(kmm._output._medoid_rows,1));
      assertTrue(contains(kmm._output._medoid_rows,7));
    } finally {
      if( fr  != null ) fr.delete();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testIris() {
    PAMModel kmm = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = fr._key;
      parms._k = 4;

      Job<PAMModel> job = new PAM(parms).trainModel();
      kmm = job.get();
      assertTrue(contains(kmm._output._medoid_rows,7));
      assertTrue(contains(kmm._output._medoid_rows,91));
      assertTrue(contains(kmm._output._medoid_rows,69));
      assertTrue(contains(kmm._output._medoid_rows,112));

      job.remove();

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testProstate() {
    PAMModel kmm = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/prostate/prostate.csv");

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = fr._key;
      parms._k = 5;

      Job<PAMModel> job = new PAM(parms).trainModel();
      kmm = job.get();
      assertTrue(contains(kmm._output._medoid_rows,49));
      assertTrue(contains(kmm._output._medoid_rows,132));
      assertTrue(contains(kmm._output._medoid_rows,214));
      assertTrue(contains(kmm._output._medoid_rows,282));
      assertTrue(contains(kmm._output._medoid_rows,357));

      job.remove();

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testProstateEuclidean() {
    PAMModel kmm = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/prostate/prostate.csv");

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = fr._key;
      parms._k = 5;
      parms._dissimilarity_measure = PAM.DissimilarityMeasure.EUCLIDEAN;

      Job<PAMModel> job = new PAM(parms).trainModel();
      kmm = job.get();
      assertTrue(contains(kmm._output._medoid_rows,47));
      assertTrue(contains(kmm._output._medoid_rows,125));
      assertTrue(contains(kmm._output._medoid_rows,193));
      assertTrue(contains(kmm._output._medoid_rows,264));
      assertTrue(contains(kmm._output._medoid_rows,341));

      job.remove();

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testProstate2() {
    PAMModel kmm = null;
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/prostate/prostate.csv");

      PAMModel.PAMParameters parms = new PAMModel.PAMParameters();
      parms._train = fr._key;
      parms._k = 8;

      Job<PAMModel> job = new PAM(parms).trainModel();
      kmm = job.get();
      assertTrue(contains(kmm._output._medoid_rows,15));
      assertTrue(contains(kmm._output._medoid_rows,40));
      assertTrue(contains(kmm._output._medoid_rows,92));
      assertTrue(contains(kmm._output._medoid_rows,129));
      assertTrue(contains(kmm._output._medoid_rows,173));
      assertTrue(contains(kmm._output._medoid_rows,240));
      assertTrue(contains(kmm._output._medoid_rows,303));
      assertTrue(contains(kmm._output._medoid_rows,357));

      job.remove();

    } finally {
      if( fr  != null ) fr .remove();
      if( kmm != null ) kmm.delete();
    }
  }

  static boolean contains ( long[] la, long ll) {
    boolean in = false;
    for (long l : la) {
      if (l == ll) in = true;
    }
    return in;
  }
}
