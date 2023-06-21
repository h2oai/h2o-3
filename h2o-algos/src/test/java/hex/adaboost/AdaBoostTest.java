package hex.adaboost;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertNotNull;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class AdaBoostTest extends TestUtil {
    
    public boolean print = false;

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void beforeClass() {
        final File h2oHomeDir = new File(System.getProperty("user.dir")).getParentFile();
        environmentVariables.set("H2O_FILES_SEARCH_PATH", h2oHomeDir.getAbsolutePath());
    }
    
    @Test
    public void testBasicTrain() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._n_estimators = 50;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainLarge() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("bigdata/laptop/creditcardfraud/creditcardfraud.csv"));
            String response = "Class";
            train.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._n_estimators = 50;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScore() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));
            Frame test = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            test.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._n_estimators = 50;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);
            
            Frame score = adaBoostModel.score(test);
            Scope.track(score);
            toCSV(score, "../prostatescore.csv");
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScoreLarge() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("bigdata/laptop/creditcardfraud/creditcardfraud.csv"));
            Frame test = Scope.track(parseTestFile("bigdata/laptop/creditcardfraud/creditcardfraud.csv"));
            String response = "Class";
            train.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._n_estimators = 50;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            Frame score = adaBoostModel.score(test);
            Scope.track(score);
            toCSV(score, "../creditcardfraudscore.csv");
        } finally {
            Scope.exit();
        }
    }    

    @Test
    public void testBasicTrainAirlines() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/testng/airlines_train_preprocessed.csv"));
            Frame test = Scope.track(parseTestFile("smalldata/testng/airlines_test_preprocessed.csv"));
            String response = "IsDepDelayed";
            train.toCategoricalCol(response);
            test.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._n_estimators = 50;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            Frame score = adaBoostModel.score(test);
            Scope.track(score);
            toCSV(score, "../airlinesscore.csv");
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainHiggs() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/higgs/higgs_train_5k.csv"));
            Frame test = Scope.track(parseTestFile("smalldata/higgs/higgs_test_5k.csv"));
            String response = "response";
            train.toCategoricalCol(response);
            test.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._n_estimators = 50;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            Frame score = adaBoostModel.score(test);
            Scope.track(score);
            toCSV(score, "../higgsscore.csv");
        } finally {
            Scope.exit();
        }
    }
    
    private void toCSV(Frame frame, String filename) {
        if (print) {
            File targetFile = new File(filename);
            try {
                FileUtils.copyInputStreamToFile(frame.toCSV(new Frame.CSVStreamParams()), targetFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
