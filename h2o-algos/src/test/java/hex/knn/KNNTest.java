package hex.knn;

import hex.ModelMetrics;
import hex.ModelMetricsClustering;
import hex.ModelMetricsMultinomial;
import hex.MultinomialAucType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.TwoDimTable;


public class KNNTest extends TestUtil {

    @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testIris() {
        KNNModel knn = null;
        Frame fr = null;
        Frame preds = null;
        Frame distances = null;
        try {
            fr = parseTestFile("smalldata/iris/iris_wheader.csv");
            
            String idColumn = "id";
            String response = "class";

            fr.add(idColumn, createIdVec(fr.numRows(), Vec.T_NUM));
            DKV.put(fr);
            KNNModel.KNNParameters parms = new KNNModel.KNNParameters();
            parms._train = fr._key;
            parms._k = 3;
            parms._distance = new EuclideanDistance();
            parms._response_column = response;
            parms._id_column = idColumn;
            parms._auc_type = MultinomialAucType.MACRO_OVR;

            parms._seed = 42;
            KNN job = new KNN(parms);
            knn = job.trainModel().get();
            
            distances = knn._output.getDistances();
            Assert.assertNotNull(distances);
            TwoDimTable distTable = distances.toTwoDimTable();
            System.out.println(distTable.toString());
            
            preds = knn.score(fr);
            System.out.println(preds.toTwoDimTable());

            ModelMetricsMultinomial mm = (ModelMetricsMultinomial) ModelMetrics.getFromDKV(knn, parms.train());
            System.out.println(mm);
            ModelMetricsMultinomial mm1 = (ModelMetricsMultinomial) knn._output._training_metrics;
            System.out.println(mm1);
            Assert.assertEquals(mm.auc(), mm1.auc(), 0);
            
            knn.testJavaScoring(fr, preds, 0);
            
        } finally {
            if (knn != null){
                knn.delete();
            }
            if (distances != null){
                distances.delete();
            }
            if(fr != null) {
                fr.delete();
            }
            if(preds != null){
                preds.delete();
            }
        }
    }

    @Test
    public void test() {
    }
}
