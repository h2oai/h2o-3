package water.bindings.examples.retrofit;

import water.bindings.H2oApi;
import water.bindings.pojos.*;
import java.io.IOException;
import java.util.Arrays;

public class ImportPatternExample {

    public static void importPatternExample(String url) throws IOException {
        H2oApi h2o = url != null ? new H2oApi(url) : new H2oApi();

        //Set url
        if (url != null) {
            h2o.setUrl(url);
        }

        //Util var
        JobV3 job = null;

        //Init h2o session
        String sessionId = h2o.newSession().sessionKey;


        //Import and parse files based on regex pattern
        { //prostate dataset (Single file)
            String pattern = "prostate_0.*"; //Regex pattern of file to import
            ImportFilesV3 importBody = h2o.importFiles("../smalldata/junit/parse_folder", pattern);
            ParseSetupV3 parseSetupParams = new ParseSetupV3();
            parseSetupParams.sourceFrames = H2oApi.stringArrayToKeyArray(importBody.destinationFrames, FrameKeyV3.class);
            parseSetupParams.checkHeader = 1;
            ParseSetupV3 parseSetupBody = h2o.guessParseSetup(parseSetupParams);

            ParseV3 parseParms = new ParseV3();
            H2oApi.copyFields(parseParms, parseSetupBody);
            parseParms.destinationFrame = H2oApi.stringToFrameKey("prostate");
            parseParms.blocking = true;
            ParseV3 parseBody = h2o.parse(parseParms);

            assert importBody.files.length == 1;
            String[] parsedFiles = new String[importBody.files.length];
            for(int i = 0; i < importBody.files.length; i ++){
                parsedFiles[i] = importBody.files[i].substring(importBody.files[0].lastIndexOf("/")+1);
            }
            String[] result = {"prostate_0.csv"};
            assert parseBody.numberColumns == 9;
            assert parseBody.rows == 10;
            String[] colNames = {"ID", "CAPSULE", "AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"};
            assert Arrays.equals(parseBody.columnNames,colNames);
        }

        { //iris dataset (Single file)
            String pattern = "iris_.*_correct.*"; //Regex pattern of file to import
            ImportFilesV3 importBody = h2o.importFiles("../smalldata/iris", pattern);
            ParseSetupV3 parseSetupParams = new ParseSetupV3();
            parseSetupParams.sourceFrames = H2oApi.stringArrayToKeyArray(importBody.destinationFrames, FrameKeyV3.class);
            parseSetupParams.checkHeader = 1;
            ParseSetupV3 parseSetupBody = h2o.guessParseSetup(parseSetupParams);

            ParseV3 parseParms = new ParseV3();
            H2oApi.copyFields(parseParms, parseSetupBody);
            parseParms.destinationFrame = H2oApi.stringToFrameKey("iris");
            parseParms.blocking = true;
            ParseV3 parseBody = h2o.parse(parseParms);

            assert importBody.files.length == 1;
            String[] parsedFiles = new String[importBody.files.length];
            for(int i = 0; i < importBody.files.length; i ++){
                parsedFiles[i] = importBody.files[i].substring(importBody.files[i].lastIndexOf("/")+1);
            }
            String[] result = {"iris_wheader_correct.csv"};
            assert Arrays.equals(parsedFiles,result);
            assert parseBody.numberColumns == 5;
            assert parseBody.rows == 150;
            String[] colNames = {"sepal_length", "sepal_width", "petal_length", "petal_width", "species"};
            assert Arrays.equals(parseBody.columnNames,colNames);
        }

        { //GBM datasets (Multiple files)
            String pattern = "50_.*"; //Regex pattern of files to import
            ImportFilesV3 importBody = h2o.importFiles("../smalldata/gbm_test", pattern);
            ParseSetupV3 parseSetupParams = new ParseSetupV3();
            parseSetupParams.sourceFrames = H2oApi.stringArrayToKeyArray(importBody.destinationFrames, FrameKeyV3.class);
            parseSetupParams.checkHeader = 1;
            ParseSetupV3 parseSetupBody = h2o.guessParseSetup(parseSetupParams);

            ParseV3 parseParms = new ParseV3();
            H2oApi.copyFields(parseParms, parseSetupBody);
            parseParms.destinationFrame = H2oApi.stringToFrameKey("50cat");
            parseParms.blocking = true;
            ParseV3 parseBody = h2o.parse(parseParms);

            assert importBody.files.length == 2;
            String[] parsedFiles = new String[importBody.files.length];
            for(int i = 0; i < importBody.files.length; i ++){
                parsedFiles[i] = importBody.files[i].substring(importBody.files[i].lastIndexOf("/")+1);
            }
            String[] result = {"50_cattest_train.csv","50_cattest_test.csv"};
            Arrays.sort(result);
            Arrays.sort(parsedFiles);
            assert Arrays.equals(parsedFiles,result);
            assert parseBody.numberColumns == 3;
            assert parseBody.rows == 5000;
            String[] colNames = {"x1","x2","y"};
            assert Arrays.equals(parseBody.columnNames,colNames);
        }

        // STEP 99: end the session
        h2o.endSession();
    }

    public static void importPatternExample() throws IOException {
        importPatternExample(null);
    }

    public static void main (String[] args) throws IOException {
        importPatternExample();
    }
}

