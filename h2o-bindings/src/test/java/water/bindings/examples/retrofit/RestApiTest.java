package water.bindings.examples.retrofit;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import okhttp3.ResponseBody;
import water.bindings.H2oApi;
import water.bindings.pojos.ColSpecifierV3;
import water.bindings.pojos.FrameKeyV3;
import water.bindings.pojos.FramesListV3;
import water.bindings.pojos.FramesV3;
import water.bindings.pojos.GBMModelV3;
import water.bindings.pojos.GBMParametersV3;
import water.bindings.pojos.GBMV3;
import water.bindings.pojos.ImportFilesV3;
import water.bindings.pojos.JobV3;
import water.bindings.pojos.ModelKeyV3;
import water.bindings.pojos.ModelsV3;
import water.bindings.pojos.ParseSetupV3;
import water.bindings.pojos.ParseV3;
import water.bindings.pojos.RapidsSchemaV3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test Rest API
 */
public class RestApiTest extends ExampleTestFixture {

    private static H2oApi API;

    private static FrameKeyV3 TRAINING_FRAME;
    private static FrameKeyV3 TEST_FRAME;
    private static ModelKeyV3 MODEL;

    @BeforeClass
    public static void setUpClass() throws IOException {
        API = new H2oApi(getH2OUrl());

        // Utility var:
        JobV3 job = null;

        // STEP 0: init a session
        String sessionId = API.newSession().sessionKey;


        // STEP 1: import raw file
        ImportFilesV3 importBody = API.importFiles(
                "http://s3.amazonaws.com/h2o-public-test-data/smalldata/flow_examples/arrhythmia.csv.gz", null
        );
        System.out.println("import: " + importBody);


        // STEP 2: parse setup
        ParseSetupV3 parseSetupBody = API.guessParseSetup(
                H2oApi.stringArrayToKeyArray(importBody.destinationFrames, FrameKeyV3.class));
        System.out.println("parseSetupBody: " + parseSetupBody);


        // STEP 3: parse into columnar Frame
        ParseV3 parseParms = new ParseV3();
        H2oApi.copyFields(parseParms, parseSetupBody);
        parseParms.destinationFrame = H2oApi.stringToFrameKey("arrhythmia.hex");
        parseParms.blocking = true;  // alternately, call h2o.waitForJobCompletion(parseSetupBody.job)

        ParseV3 parseBody = API.parse(parseParms);
        System.out.println("parseBody: " + parseBody);


        // STEP 4: Split into test and train datasets
        String tmpVec = "tmp_" + UUID.randomUUID().toString();
        String splitExpr =
                "(, " +
                        "  (tmp= " + tmpVec + " (h2o.runif arrhythmia.hex 906317))" +
                        "  (assign train " +
                        "    (rows arrhythmia.hex (<= " + tmpVec + " 0.75)))" +
                        "  (assign test " +
                        "    (rows arrhythmia.hex (> " + tmpVec + " 0.75)))" +
                        "  (rm " + tmpVec + "))";
        RapidsSchemaV3 rapidsParms = new RapidsSchemaV3();
        rapidsParms.sessionId = sessionId;
        rapidsParms.ast = splitExpr;
        API.rapidsExec(rapidsParms);


        // STEP 5: Train the model (NOTE: step 4 is polling, which we don't require because we specified blocking for the parse above)
        GBMParametersV3 gbmParms = new GBMParametersV3();

        // gbmParms.trainingFrame = H2oApi.stringToFrameKey("arrhythmia.hex");

        gbmParms.trainingFrame = H2oApi.stringToFrameKey("train");
        TRAINING_FRAME = gbmParms.trainingFrame;
        gbmParms.validationFrame = H2oApi.stringToFrameKey("test");
        TEST_FRAME = gbmParms.validationFrame;

        ColSpecifierV3 responseColumn = new ColSpecifierV3();
        responseColumn.columnName = "C1";
        gbmParms.responseColumn = responseColumn;

        System.out.println("About to train GBM. . .");
        GBMV3 gbmBody = API.train_gbm(gbmParms);
        System.out.println("gbmBody: " + gbmBody);


        // STEP 6: poll for completion
        job = API.waitForJobCompletion(gbmBody.job.key);
        System.out.println("GBM build done.");


        // STEP 7: fetch the model
        ModelKeyV3 model_key = (ModelKeyV3) job.dest;
        MODEL = model_key;
        ModelsV3 models = API.model(model_key);
        System.out.println("models: " + models);
        GBMModelV3 model = (GBMModelV3) models.models[0];
        System.out.println("new GBM model: " + model);
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        API.endSession();
    }

    @Test
    public void testLoadFramesWithKey() throws IOException {
        FramesListV3 frames = API.frames(TRAINING_FRAME);
        assertNotNull(frames);
        assertNotNull(frames.frames);
        assertEquals(7, frames.frames.length);
    }

    @Test
    public void testLoadFrameWithKey() throws IOException {
        FramesV3 frame = API.frame(TEST_FRAME);
        assertNotNull(frame);
    }

    @Test
    public void testDownloadDataSetWithKey() throws IOException {
        ResponseBody data = API._downloadDataset_fetch(TEST_FRAME);
        assertNotNull(data);
        File h2oFile = File.createTempFile("h2o_data_", ".csv");
        FileUtils.copyInputStreamToFile(data.byteStream(), h2oFile);

        assertTrue(h2oFile.length() > 0);

        if (!h2oFile.delete()) {
            h2oFile.deleteOnExit();
        }
    }

}
