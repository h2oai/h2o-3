package hex.tree.isoforextended;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ExtendedIsolationForestTest extends TestUtil {

    @BeforeClass()
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testBasic() {
        try {
            Scope.enter();
            Frame train = Scope.track(parse_test_file("smalldata/anomaly/single_blob.csv"));

            ExtendedIsolationForestParameters p = new ExtendedIsolationForestParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._ntrees = 100;
            p.extensionLevel = train.numCols() - 1;
            p._min_rows = 1;
//            p._sample_size = 5;

            ExtendedIsolationForest eif = new ExtendedIsolationForest(p);
            ExtendedIsolationForestModel model = eif.trainModel().get();
            assertNotNull(model);
            model.iTrees = eif.iTrees;

            Frame test = Scope.track(parse_test_file("smalldata/anomaly/heatmap_data.csv"));
            Frame out = model.score(test);
            Frame.CSVStreamParams params = new Frame.CSVStreamParams();
            byte[] buffer = new byte[1<<20];
            int bytesRead;
            try {
                try (InputStream frameToStream = out.toCSV(params);
                     OutputStream outStream = new FileOutputStream("eif.csv")) {
                    while((bytesRead=frameToStream.read(buffer)) > 0) { // for our toCSV stream, return 0 as EOF, not -1
                        outStream.write(buffer, 0, bytesRead);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void avgPathLengTest() {
        assertEquals(10.244770920116851, IsolationTree.averagePathLengthOfUnsuccesfullSearch(256), 1e-5);
        assertEquals(11.583643521303037, IsolationTree.averagePathLengthOfUnsuccesfullSearch(500), 1e-5);
        assertEquals(0, IsolationTree.averagePathLengthOfUnsuccesfullSearch(1), 1e-5);
        assertEquals(0, IsolationTree.averagePathLengthOfUnsuccesfullSearch(0), 1e-5);
        assertEquals(0, IsolationTree.averagePathLengthOfUnsuccesfullSearch(-1), 1e-5);
    }
}
