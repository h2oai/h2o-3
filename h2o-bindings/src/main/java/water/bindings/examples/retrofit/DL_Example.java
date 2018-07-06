package water.bindings.examples.retrofit;

import water.bindings.H2oApi;
import water.bindings.pojos.*;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by laurend on 6/1/18.
 */
public class DL_Example {
    public static void dlExampleFlow(String url) throws IOException {
        //H2O start
        //String url = "http://localhost:54321/";
        H2oApi h2o = new H2oApi(url);

        //STEP 0: init a session
        String sessionId = h2o.newSession().sessionKey;

        //STEP 1: import raw file
        //String path = "hdfs://kbmst:9000/user/spark/datasets/iris.csv";
        String path = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris.csv";
        ImportFilesV3 importBody = h2o.importFiles(path, null);
        //System.out.println("import: " + importBody);

        //STEP 2: parse setup
        ParseSetupV3 parseSetupParams = new ParseSetupV3();
        parseSetupParams.sourceFrames = H2oApi.stringArrayToKeyArray(importBody.destinationFrames, FrameKeyV3.class);
        ParseSetupV3 parseSetupBody = h2o.guessParseSetup(parseSetupParams);
        //System.out.println("parseSetupBody: " + parseSetupBody);

        //STEP 3: parse into columnar Frame
        ParseV3 parseParams = new ParseV3();
        H2oApi.copyFields(parseParams, parseSetupBody);
        parseParams.destinationFrame = H2oApi.stringToFrameKey("iris.hex");
        parseParams.blocking = true;
        ParseV3 parseBody = h2o.parse(parseParams);
        System.out.println("parseBody: " + parseBody);

        //STEP 4: Split into test and train datasets
        String tmpVec = "tmp_" + UUID.randomUUID().toString();
        String splitExpr =
                "(, " +
                        "  (tmp= " + tmpVec + " (h2o.runif iris.hex 906317))" +
                        "  (assign train " +
                        "    (rows iris.hex (<= " + tmpVec + " 0.75)))" +
                        "  (assign test " +
                        "    (rows iris.hex (> " + tmpVec + " 0.75)))" +
                        "  (rm " + tmpVec + "))";
        RapidsSchemaV3 rapidsParams = new RapidsSchemaV3();
        rapidsParams.sessionId = sessionId;
        rapidsParams.ast = splitExpr;
        h2o.rapidsExec(rapidsParams);

        // STEP 5: Train the model
        // (NOTE: step 4 is polling, which we don't require because we specified blocking for the parse above)
        DeepLearningParametersV3 dlParams = new DeepLearningParametersV3();
        dlParams.trainingFrame = H2oApi.stringToFrameKey("train");
        dlParams.validationFrame = H2oApi.stringToFrameKey("test");

        dlParams.hidden=new int[]{200,200};

        ColSpecifierV3 responseColumn = new ColSpecifierV3();
        responseColumn.columnName = "C1";
        dlParams.responseColumn = responseColumn;

        System.out.println("About to train DL. . .");
        DeepLearningV3 dlBody = h2o.train_deeplearning(dlParams);
        //System.out.println("dlBody: " + dlBody);

        // STEP 6: poll for completion
        JobV3 job = h2o.waitForJobCompletion(dlBody.job.key);
        System.out.println("DL build done.");

        // STEP 7: fetch the model
        ModelKeyV3 model_key = (ModelKeyV3)job.dest;
        ModelsV3 models = h2o.model(model_key);
        System.out.println("models: " + models);
        DeepLearningModelV3 model = (DeepLearningModelV3)models.models[0];
        System.out.println("new DL model: " + model);


        // STEP 8: predict!
        ModelMetricsListSchemaV3 predict_params = new ModelMetricsListSchemaV3();
        predict_params.model = model_key;
        predict_params.frame = dlParams.trainingFrame;
        predict_params.predictionsFrame = H2oApi.stringToFrameKey("predictions");

        ModelMetricsListSchemaV3 predictions = h2o.predict(predict_params);
        System.out.println("predictions: " + predictions);

        // STEP 9: end the session
        h2o.endSession();
    }
}
