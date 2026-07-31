package hex.knn;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Chunk;
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
            fr.add(idColumn, createIdVec(fr.numRows(), Vec.T_NUM));
            Scope.track(fr);
            int k = 3;
            int nCols = fr.numCols();
            Chunk[] query = new Chunk[nCols];
            for (int j = 0; j < nCols; j++) {
                query[j] = fr.vec(j).chunkForChunkIdx(0);
            }
            KNNDistanceTask mrt = new KNNDistanceTask(k, query, new EuclideanDistance(), fr.find(idColumn), idColumn, fr.vec(idColumn).get_type(), fr.find(response), response);
            mrt.doAll(fr);
            Frame result = mrt.outputFrame();
            Scope.track(result);
            Assert.assertNotNull(result);
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
            fr.add(idColumn, createIdVec(fr.numRows(), Vec.T_NUM));
            Scope.track(fr);
            int k = 3;
            int nCols = fr.numCols();
            Chunk[] query = new Chunk[nCols];
            for (int j = 0; j < nCols; j++) {
                query[j] = fr.vec(j).chunkForChunkIdx(0);
            }
            KNNDistanceTask mrt = new KNNDistanceTask(k, query, new ManhattanDistance(), fr.find(idColumn), idColumn, fr.vec(idColumn).get_type(), fr.find(response), response);
            mrt.doAll(fr);
            Frame result = mrt.outputFrame();
            Scope.track(result);
            Assert.assertNotNull(result);
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
            fr.add(idColumn, createIdVec(fr.numRows(), Vec.T_NUM));
            Scope.track(fr);
            int k = 3;
            int nCols = fr.numCols();
            Chunk[] query = new Chunk[nCols];
            for (int j = 0; j < nCols; j++) {
                query[j] = fr.vec(j).chunkForChunkIdx(0);
            }
            KNNDistanceTask mrt = new KNNDistanceTask(k, query, new CosineDistance(), fr.find(idColumn), idColumn, fr.vec(idColumn).get_type(), fr.find(response), response);
            mrt.doAll(fr);
            Frame result = mrt.outputFrame();
            Scope.track(result);
            Assert.assertNotNull(result);
        }
        finally {
            Scope.exit();
        }
    }
}
