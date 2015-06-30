package hex.tree.drf;

import static org.testng.Assert.*;
import org.testng.annotations.*;

import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DRFBasic extends TestNGUtil {
    static Frame _airquality;
    static Frame _insurance;

    @DataProvider(name = "glmCases")
    public static Object[][] glmCases() {
        Object[][] data = null;
        try {
            List<String> lines = Files.readAllLines(Paths.get("h2o-testng/src/test/" +
                    "resources/drfCases.csv"), Charset.defaultCharset());
            data = new Object[lines.size()][17];
            int r = 0;
            for(String line : lines){
                String[] variables = line.trim().split(",");
                for(int c = 0; c < 17; c++){
                    try{ data[r][c] = variables[c]; } catch(IndexOutOfBoundsException e) { data[r][c] = "";}
                }
                r++;
            }
        } catch(Exception ignore) {}

        return data;
    }

    @Test(dataProvider = "glmCases")
    public void basic(String regression,String classification, String gaussian, String binomial, String poissan,
                      String gamma, String ignore_const_cols, String score_each_iteration, String trees,
                      String max_depth, String min_rows, String n_bins, String m_tries, String sample_rate,
                      String balance_classes, String class_sampling_factors, String dataset) {
        // Get drf parameters
        int nt = trees.equals("") ? 50 : Integer.parseInt(trees);
        int md = max_depth.equals("") ? 5 : Integer.parseInt(max_depth);
        int mr = min_rows.equals("") ? 10 : Integer.parseInt(min_rows);
        int nb = n_bins.equals("") ? 20 : Integer.parseInt(n_bins);
        int mt = m_tries.equals("") ? -1 : Integer.parseInt(m_tries);
        float sr = sample_rate.equals("") ? 0.632f : Float.parseFloat(sample_rate);
        boolean bc = balance_classes.equals("x");

        DRF job = null;
        DRFModel drf = null;
        Frame score = null;

        try {
            Scope.enter();

            // convert appropriate columns to enum
            if(dataset.equals("airquality.csv") && binomial.equals("x")) {
                File airquality = find_test_file_static("smalldata/glm_test/airquality.csv");
                assert airquality.exists();
                NFSFileVec nfs_airquality = NFSFileVec.make(airquality);
                Key airqualityKey = Key.make("airquality.hex");
                _airquality = ParseDataset.parse(airqualityKey, nfs_airquality._key);

                for (int i : new int[]{4, 5}) {
                    _airquality.vecs()[i] = _airquality.vecs()[i].toEnum();
                }

                DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
                parms._train = _airquality._key;
                parms._response_column = "Month";
                parms._nbins = nb;
                parms._ntrees = nt;
                parms._max_depth = md;
                parms._mtries = mt;
                parms._sample_rate = sr;
                parms._min_rows = mr;

                // Build a first model; all remaining models should be equal
                job = new DRF(parms);
                drf = job.trainModel().get();
                System.out.println("Test set MSE: " + drf._output._training_metrics._MSE);

                job.remove();
                drf.delete();
            }
        } finally{
            if (_insurance != null) _insurance.remove();
            if (_airquality != null) _airquality.remove();
        }
        Scope.exit();
    }

    @BeforeClass
    public static void setup() {
        File insurance = find_test_file_static("smalldata/glm_test/insurance.csv");
        assert insurance.exists();
        NFSFileVec nfs_insurance = NFSFileVec.make(insurance);
        Key insuranceKey = Key.make("insurance.hex");
        _insurance = ParseDataset.parse(insuranceKey,  nfs_insurance._key);
    }

    @AfterClass
    public void cleanUp() {
        if(_airquality != null)
            _airquality.delete();

        if(_insurance != null)
            _insurance.delete();
    }
}
