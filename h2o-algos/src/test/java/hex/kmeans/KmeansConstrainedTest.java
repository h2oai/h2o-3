package hex.kmeans;

import hex.ModelMetricsClustering;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.Assume;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class KmeansConstrainedTest extends TestUtil {

    @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

    @Test public void testSimpleConstrained() {
        KMeansModel kmm = null;
        Frame fr = null, fr2 = null, points = null;
        try {

            fr = new Frame(Key.<Frame>make(), new String[]{"x", "y"}, new Vec[]{Vec.makeVec(new long[]{1, 2, 4}, null, Vec.newKey()),
                    Vec.makeVec(new long[]{1, 2, 3}, null, Vec.newKey())});
            DKV.put(fr);

            points = new Frame(Key.<Frame>make(), new String[]{"x", "y"}, new Vec[]{Vec.makeVec(new long[]{1, 3}, null, Vec.newKey()),
                    Vec.makeVec(new long[]{2, 4}, null, Vec.newKey())});
            DKV.put(points);

            KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
            parms._train = fr._key;
            parms._k = 2;
            parms._standardize = false;
            parms._max_iterations = 10;
            parms._cluster_size_constraints = new int[]{1, 1};
            parms._user_points = points._key;
            KMeans job = new KMeans(parms);
            kmm = job.trainModel().get();

            // Done building model; produce a score column with cluster choices
            fr2 = kmm.score(fr);
            for(int i=0; i<parms._k; i++) {
                assert kmm._output._size[i] >= parms._cluster_size_constraints[i] : "Minimal size of cluster "+(i+1)+" should be "+parms._cluster_size_constraints[i]+" but is "+kmm._output._size[i]+".";
            }

        } finally {
            if( fr  != null ) fr.delete();
            if( fr2 != null ) fr2.delete();
            if( points != null ) points.delete();
            if( kmm != null ) kmm.delete();
        }
    }

    @Test
    public void testNfoldsConstrained() {
        Frame tfr = null, points = null;
        KMeansModel kmeans = null;

        Scope.enter();
        try {
            points = ArrayUtils.frame(ard(
                    ard(6.0,2.2,4.0,1.0,0),
                    ard(5.2,3.4,1.4,0.2,1),
                    ard(6.9,3.1,5.4,2.1,2)
            ));

            tfr = parseTestFile("./smalldata/iris/iris_wheader.csv");
            DKV.put(tfr);
            KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
            parms._train = tfr._key;
            parms._seed = 0xdecaf;
            parms._k = 3;
            parms._cluster_size_constraints = new int[]{20, 20, 20};
            parms._nfolds = 3;
            parms._user_points = points._key;

            KMeans job = new KMeans(parms);
            kmeans = job.trainModel().get();

            ModelMetricsClustering mm = (ModelMetricsClustering)kmeans._output._cross_validation_metrics;
            assertNotNull(mm);
        } finally {
            if (tfr != null) tfr.remove();
            if (kmeans != null) {
                kmeans.deleteCrossValidationModels();
                kmeans.delete();
            }
            if(points != null) points.remove();
            Scope.exit();
        }
    }

    @Test
    public void testIrisConstrained() {
        KMeansModel kmm = null, kmm2 = null, kmm3 = null, kmm4 = null;
        Frame fr = null, points=null, predict1=null, predict2=null, predict3=null, predict4=null;
        try {
            Scope.enter();
            fr = Scope.track(parseTestFile("./smalldata/iris/iris_wheader.csv"));

            points = ArrayUtils.frame(ard(
                    ard(4.9, 3.0, 1.4, 0.2),
                    ard(5.6, 2.5, 3.9, 1.1),
                    ard(6.5, 3.0, 5.2, 2.0)
            ));

            KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
            parms._train = fr._key;
            parms._k = 3;
            parms._standardize = true;
            parms._max_iterations = 10;
            parms._user_points = points._key;
            parms._cluster_size_constraints = new int[]{49, 46, 55};
            parms._score_each_iteration = true;
            parms._ignored_columns = new String[]{"class"};

            System.out.println("Constrained Kmeans strandardize true (CKT)");
            KMeans job = new KMeans(parms);
            kmm = (KMeansModel) Scope.track_generic(job.trainModel().get());

            for(int i=0; i<parms._k; i++) {
                System.out.println(kmm._output._size[i]+">="+parms._cluster_size_constraints[i]);
                assert kmm._output._size[i] >= parms._cluster_size_constraints[i] : "Minimal size of cluster "+(i+1)+" should be "+parms._cluster_size_constraints[i]+" but is "+kmm._output._size[i]+".";
            }

            KMeansModel.KMeansParameters parms3 = new KMeansModel.KMeansParameters();
            parms3._train = fr._key;
            parms3._k = 3;
            parms3._standardize = true;
            parms3._max_iterations = 10;
            parms3._user_points = points._key;
            parms3._score_each_iteration = true;
            parms3._ignored_columns = new String[]{"class"};

            System.out.println("Loyd Kmeans strandardize true (FKT)");
            KMeans job3 = new KMeans(parms3);
            kmm3 = (KMeansModel) Scope.track_generic(job3.trainModel().get());

            KMeansModel.KMeansParameters parms2 = new KMeansModel.KMeansParameters();
            parms2._train = fr._key;
            parms2._k = 3;
            parms2._standardize = false;
            parms2._max_iterations = 10;
            parms2._user_points = points._key;
            parms2._score_each_iteration = true;
            parms2._ignored_columns = new String[]{"class"};
            parms2._cluster_size_constraints = new int[]{50, 61, 39};

            System.out.println("Constrained Kmeans strandardize false (CKF)");
            KMeans job2 = new KMeans(parms2);
            kmm2 = (KMeansModel) Scope.track_generic(job2.trainModel().get());

            for(int i=0; i<parms2._k; i++) {
                System.out.println(kmm2._output._size[i]+">="+parms2._cluster_size_constraints[i]);
                assert kmm2._output._size[i] >= parms2._cluster_size_constraints[i] : "Minimal size of cluster "+(i+1)+" should be "+parms2._cluster_size_constraints[i]+" but is "+kmm2._output._size[i]+".";
            }

            KMeansModel.KMeansParameters parms4 = new KMeansModel.KMeansParameters();
            parms4._train = fr._key;
            parms4._k = 3;
            parms4._standardize = false;
            parms4._max_iterations = 10;
            parms4._user_points = points._key;
            parms4._score_each_iteration = true;
            parms4._ignored_columns = new String[]{"class"};

            System.out.println("Loyd Kmeans strandardize false (FKF)");
            KMeans job4 = new KMeans(parms4);
            kmm4 = (KMeansModel) Scope.track_generic(job4.trainModel().get());


            predict1 = kmm.score(fr);
            predict2 = kmm2.score(fr);
            predict3 = kmm3.score(fr);
            predict4 = kmm4.score(fr);

            System.out.println("\nPredictions:");
            System.out.println("  | CKT | FKT | CKF | FKF |");
            for (int i=0; i<fr.numRows(); i++){
                System.out.println(i + " |  " + predict1.vec(0).at8(i) + "  |  " + predict3.vec(0).at8(i) + "  |  " + predict2.vec(0).at8(i) + "  |  " + predict4.vec(0).at8(i) + "  |");
                assert predict1.vec(0).at8(i) == predict3.vec(0).at8(i): "Predictions should be the same for Loyd Kmenas and Constrained Kmeans.";
                assert predict2.vec(0).at8(i) == predict4.vec(0).at8(i): "Predictions should be the same for Loyd Kmenas and Constrained Kmeans.";
            }

            System.out.println("\nCenters raw:");
            for (int i=0; i<kmm._output._centers_raw.length;i++){
                System.out.println("===");
                for (int j=0; j<kmm._output._centers_raw[0].length;j++) {
                    System.out.println(kmm._output._centers_raw[i][j]+" == "+kmm3._output._centers_raw[i][j]+" | "+kmm2._output._centers_raw[i][j]+" == "+kmm4._output._centers_raw[i][j]);
                    assertEquals(kmm._output._centers_raw[i][j], kmm3._output._centers_raw[i][j], 1e-1);
                    assertEquals(kmm2._output._centers_raw[i][j], kmm4._output._centers_raw[i][j], 1e-1);
                }
            }

        } finally {
            if( fr  != null ) fr.delete();
            if( points != null ) points.delete();
            if( kmm != null ) kmm.delete();
            if( kmm2 != null ) kmm2.delete();
            if( kmm3 != null ) kmm3.delete();
            if( kmm4 != null ) kmm4.delete();
            if( predict1 != null ) predict1.delete();
            if( predict2 != null ) predict2.delete();
            if( predict3 != null ) predict3.delete();
            if( predict4 != null ) predict4.delete();
            Scope.exit();
        }
    }

    @Test
    public void testWeatherChicagoConstrained() {
        Assume.assumeTrue(H2O.getCloudSize() == 1); // don't test in multi-node, not worth it - this tests already takes a long time
        KMeansModel kmm = null, kmm2 = null;
        Frame fr = null, points = null;
        try {
            Scope.enter();
            fr = Scope.track(parseTestFile("./smalldata/chicago/chicagoAllWeather.csv"));
            points = ArrayUtils.frame(ard(
                    ard(0.9223065747871615,1.016292569726567,1.737905586557139,-0.2732881352142627,0.8408705963844509,-0.2664469441473223,-0.2881728818872508),
                    ard(-1.4846149848792978,-1.5780763628717547,-1.330641758390853,-1.3664503532612082,-1.0180638458160431,-1.1194221247071547,-1.2345088149586547),
                    ard(1.4953511836400069,-1.001549933405461,-1.4442916600555933,1.5766442462663375,-1.855936520243046,-2.07274732650932,-2.2859931850379924)));

            KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
            parms._train = fr._key;
            parms._seed = 0xcaf;
            parms._k = 3;
            parms._cluster_size_constraints = new int[]{1000, 3000, 1000};
            parms._user_points = points._key;
            parms._standardize = true;
            parms._max_iterations = 3;

            KMeans job = new KMeans(parms);
            kmm = (KMeansModel) Scope.track_generic(job.trainModel().get());

            for(int i=0; i<parms._k; i++) {
                System.out.println(kmm._output._size[i]+">="+parms._cluster_size_constraints[i]);
                assert kmm._output._size[i] >= parms._cluster_size_constraints[i] : "Minimal size of cluster "+(i+1)+" should be "+parms._cluster_size_constraints[i]+" but is "+kmm._output._size[i]+".";
            }

            parms._standardize = false;
            KMeans job2 = new KMeans(parms);
            kmm2 = (KMeansModel) Scope.track_generic(job2.trainModel().get());

            for(int i=0; i<parms._k; i++) {
                System.out.println(kmm2._output._size[i]+">="+parms._cluster_size_constraints[i]);
                assert kmm2._output._size[i] >= parms._cluster_size_constraints[i] : "Minimal size of cluster "+(i+1)+" should be "+parms._cluster_size_constraints[i]+" but is "+kmm2._output._size[i]+".";
            }

        } finally {
            if( fr  != null ) fr.delete();
            if( points != null ) points.delete();
            if( kmm != null ) kmm.delete();
            if( kmm2 != null ) kmm2.delete();
            Scope.exit();
        }
    }

    @Test @Ignore
    public void testMnistConstrained() {
        KMeansModel kmm = null, kmm2 = null;
        Frame fr = null, points = null;
        try {
            Scope.enter();
            fr = Scope.track(parseTestFile("bigdata/laptop/mnist/train.csv.gz"));

            KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
            parms._train = fr._key;
            parms._seed = 0xcaf;
            parms._k = 3;
            parms._cluster_size_constraints = new int[]{10000, 30000, 10000};
            parms._init = KMeans.Initialization.Furthest;
            parms._standardize = true;
            parms._max_iterations = 3;
            parms._ignored_columns = new String[]{"1023"};


            KMeans job = new KMeans(parms);
            kmm = (KMeansModel) Scope.track_generic(job.trainModel().get());

        } finally {
            if( fr  != null ) fr.delete();
            if( points != null ) points.delete();
            if( kmm != null ) kmm.delete();
            if( kmm2 != null ) kmm2.delete();
            Scope.exit();
        }
    }
}
