package hex.tree.gbm;

import hex.tree.SharedTreeModel;

import water.*;
import water.fvec.*;

import static org.testng.Assert.*;
import org.testng.annotations.*;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class GBMBasic extends TestNGUtil {

    @DataProvider(name = "glmData")
    public static Object[][] glmData() {
        Object[][] data = null;
        try {
            List<String> lines = Files.readAllLines(Paths.get("h2o-testng/src/test/" +
                    "resources/glmData.csv"), Charset.defaultCharset());
            data = new Object[lines.size()][5];
            int r = 0;
            for(String line : lines){
                int c = 0;
                for(String variable : line.trim().split(",")) {
                    data[r][c] = variable;
                    c++;
                }
                r++;
            }
        } catch(Exception ignore) {}

        return data;
    }
    private abstract class PrepData {
        abstract int prep(Frame fr);
    }

    static final String ignored_aircols[] = new String[]{"DepTime", "ArrTime", "AirTime", "ArrDelay", "DepDelay", "TaxiIn", "TaxiOut", "Cancelled", "CancellationCode", "Diverted", "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsDepDelayed"};

    @Test(dataProvider = "glmData")
    public void testGBMRegressionGaussian(String one, String two, String three, String four, String five) {
        GBMModel gbm = null;
        Frame fr = null, fr2 = null;
        try {
            fr = parse_test_file("smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = fr._key;
            parms._distribution = SharedTreeModel.SharedTreeParameters.Family.gaussian;
            parms._response_column = fr._names[1]; // Row in col 0, dependent in col 1, predictor in col 2
            parms._ntrees = 1;
            parms._max_depth = 1;
            parms._min_rows = 1;
            parms._nbins = 20;
            // Drop ColV2 0 (row), keep 1 (response), keep col 2 (only predictor), drop remaining cols
            String[] xcols = parms._ignored_columns = new String[fr.numCols() - 2];
            xcols[0] = fr._names[0];
            System.arraycopy(fr._names, 3, xcols, 1, fr.numCols() - 3);
            parms._learn_rate = 1.0f;
            parms._score_each_iteration = true;

            GBM job = null;
            try {
                job = new GBM(parms);
                gbm = job.trainModel().get();
            } finally {
                if (job != null) job.remove();
            }
            assertTrue(job._state == water.Job.JobState.DONE);

            // Done building model; produce a score column with predictions
            fr2 = gbm.score(fr);
        } finally {
            if (fr != null) fr.remove();
            if (fr2 != null) fr2.remove();
            if (gbm != null) gbm.delete();
        }
    }
}
