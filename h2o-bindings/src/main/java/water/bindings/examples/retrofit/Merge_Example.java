package water.bindings.examples.retrofit;

import water.bindings.H2oApi;
import water.bindings.pojos.*;
import water.bindings.proxies.retrofit.*;

import java.io.IOException;
import java.util.UUID;

public class Merge_Example {

    public static void mergeExample() {
        H2oApi h2o = new H2oApi();

        try {
            // Utility var:
            JobV3 job = null;

            // init a session
            String sessionId = h2o.newSession().sessionKey;


            // import and parse files
            { // tourism
              ImportFilesV3 importBody = h2o.importFiles("smalldata/merge/tourism.csv");
              ParseSetupV3 parseSetupParams = new ParseSetupV3();
              parseSetupParams.sourceFrames = H2oApi.stringArrayToKeyArray(importBody.destinationFrames, FrameKeyV3.class);
              parseSetupParams.checkHeader = 1;
              ParseSetupV3 parseSetupBody = h2o.guessParseSetup(parseSetupParams);

              ParseV3 parseParms = new ParseV3();
              H2oApi.copyFields(parseParms, parseSetupBody);
              parseParms.destinationFrame = H2oApi.stringToFrameKey("tourism");
              parseParms.blocking = true;
              ParseV3 parseBody = h2o.parse(parseParms);
            }

            { // heart
              ImportFilesV3 importBody = h2o.importFiles("smalldata/merge/heart.csv");
              ParseSetupV3 parseSetupParams = new ParseSetupV3();
              parseSetupParams.sourceFrames = H2oApi.stringArrayToKeyArray(importBody.destinationFrames, FrameKeyV3.class);
              parseSetupParams.checkHeader = 1;
              ParseSetupV3 parseSetupBody = h2o.guessParseSetup(parseSetupParams);

              ParseV3 parseParms = new ParseV3();
              H2oApi.copyFields(parseParms, parseSetupBody);
              parseParms.destinationFrame = H2oApi.stringToFrameKey("heart");
              parseParms.checkHeader = 1;
              parseParms.blocking = true;
              ParseV3 parseBody = h2o.parse(parseParms);
            }

            // convert heart.geotime to categorical / factor

            RapidsSchemaV3 rapidsParms = new RapidsSchemaV3();
            rapidsParms.sessionId = sessionId;
            rapidsParms.ast = "(assign heart (:= heart (as.factor (cols heart 1)) 1 [0:263]))";
            h2o.rapidsExec(rapidsParms);

            // merge datasets
            rapidsParms.ast = String.format("(assign mergedframe (merge %s %s TRUE FALSE [] [] \"auto\") )",
                                                "tourism",
                                                "heart");
            h2o.rapidsExec(rapidsParms);

            // STEP 99: end the session
            h2o.endSession();
        }
        catch (IOException e) {
            System.err.println("Caught exception: " + e);
        }
    }

    public static void main (String[] args) {
        mergeExample();
    }
}

