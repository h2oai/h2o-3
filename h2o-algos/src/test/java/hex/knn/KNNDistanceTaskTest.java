package hex.knn;

import org.apache.commons.lang.math.LongRange;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.TwoDimTable;

public class KNNDistanceTaskTest extends TestUtil {

    @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testIrisEuclidean(){
        try {
            Scope.enter();
            Frame fr = parseTestFile("smalldata/iris/iris_wheader.csv");
            Scope.track(fr);
            String idColumn = "id";
            String response = "class";
            int frRows = (int) fr.numRows();
            fr.add(idColumn, createIdVec(fr.numRows(), Vec.T_NUM));
            int k = 3;
            Frame query = fr.deepSlice(new LongRange(0, frRows-1).toArray(), null);      
            Scope.track(fr);
            KNNDistanceTask mrt = new KNNDistanceTask(k, query, new EuclideanDistance(), idColumn, response);
            mrt.doAll(fr);
            Frame result = mrt.outputFrame();
            Scope.track(result);
            TwoDimTable queryTable = query.toTwoDimTable(0, frRows);
            System.out.println(queryTable.toString());
            TwoDimTable resultTable = result.toTwoDimTable(0, frRows);
            System.out.println(resultTable.toString());
        }
        finally {
           Scope.exit();
        }
    }

    @Test
    public void testIrisManhattan(){
        try {
            Scope.enter();
            Frame fr = parseTestFile("smalldata/iris/iris_wheader.csv");
            Scope.track(fr);
            String idColumn = "id";
            String response = "class";
            int frRows = (int) fr.numRows();
            fr.add(idColumn, createIdVec(fr.numRows(), Vec.T_NUM));
            int k = 3;
            Frame query = fr.deepSlice(new LongRange(0, frRows-1).toArray(), null);
            Scope.track(fr);
            KNNDistanceTask mrt = new KNNDistanceTask(k, query, new ManhattanDistance(), idColumn, response);
            mrt.doAll(fr);
            Frame result = mrt.outputFrame();
            Scope.track(result);
            TwoDimTable queryTable = query.toTwoDimTable(0, frRows);
            System.out.println(queryTable.toString());
            TwoDimTable resultTable = result.toTwoDimTable(0, frRows);
            System.out.println(resultTable.toString());
        }
        finally {
            Scope.exit();
        }
    }

    @Test
    public void testIrisCosine(){
        try {
            Scope.enter();
            Frame fr = parseTestFile("smalldata/iris/iris_wheader.csv");
            Scope.track(fr);
            String idColumn = "id";
            String response = "class";
            int frRows = (int) fr.numRows();
            fr.add(idColumn, createIdVec(frRows, Vec.T_NUM));
            int k = 3;
            Frame query = fr.deepSlice(new LongRange(0, frRows-1).toArray(), null);
            Scope.track(fr);
            KNNDistanceTask mrt = new KNNDistanceTask(k, query, new CosineDistance(), idColumn, response);
            mrt.doAll(fr);
            Frame result = mrt.outputFrame();
            Scope.track(result);
            TwoDimTable queryTable = query.toTwoDimTable(0, frRows);
            System.out.println(queryTable.toString());
            TwoDimTable resultTable = result.toTwoDimTable(0, frRows);
            System.out.println(resultTable.toString());
        }
        finally {
            Scope.exit();
        }
    }
}
