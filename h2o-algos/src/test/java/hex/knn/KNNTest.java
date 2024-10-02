package hex.knn;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

public class KNNTest extends TestUtil {

    @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testIris() {
        KNNModel knn = null;
        Frame fr = null, fr2 = null;
        try {
            fr = parseTestFile("smalldata/iris/iris_wheader.csv");

            KNNModel.KNNParameters parms = new KNNModel.KNNParameters();
            parms._train = fr._key;
            parms._k = 3;
            parms._distance = new EuclideanDistance();

            parms._seed = 42;
            KNN job = new KNN(parms);
            knn = job.trainModel().get();

            Frame distances = knn._output._distances;
            Assert.assertNotNull(distances);
            fr2 = knn.score(fr);
        } finally {
            if( fr  != null ) fr.delete();
            if( fr2 != null ) fr2.delete();
            if( knn != null ) knn.delete();
        }
    }
}
