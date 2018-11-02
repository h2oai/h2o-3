package water.bindings.examples;

import com.google.gson.*;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import water.bindings.pojos.*;
import water.bindings.proxies.retrofit.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Example of using generated Java bindings for REST API.
 *
 * Before use, please, make sure you generated the bindings
 * via Gradle: `gradlew
 */
public class Example {

    /**
     * Keys get sent as Strings and returned as objects also containing the type and URL,
     * so they need a custom GSON serializer.
     */
    private static class KeySerializer implements JsonSerializer<KeyV3> {
        public JsonElement serialize(KeyV3 key, Type typeOfKey, JsonSerializationContext context) {
            return new JsonPrimitive(key.name);
        }
    }

    /**
     * KeysColSpecifiers get sent as Strings and returned as objects also containing a list of Frames that the col must be a member of,
     * so they need a custom GSON serializer.
    private static class ColSpecifierSerializer implements JsonSerializer<ColSpecifierV3> {
        public JsonElement serialize(ColSpecifierV3 cs, Type t, JsonSerializationContext context) {
            return new JsonPrimitive(cs.column_name);
        }
    }
     */

    public static JobV3 poll(Retrofit retrofit, String job_id) {
        Jobs jobsService = retrofit.create(Jobs.class);
        Response<JobsV3> jobsResponse = null;

        int retries = 3;
        JobsV3 jobs = null;
        do {
            try {
                jobsResponse = jobsService.fetch(job_id).execute();
            }
            catch (IOException e) {
                System.err.println("Caught exception: " + e);
            }
            if (! jobsResponse.isSuccessful())
                if (retries-- > 0)
                   continue;
                else
                    throw new RuntimeException("/3/Jobs/{job_id} failed 3 times.");

            jobs = jobsResponse.body();
            if (null == jobs.jobs || jobs.jobs.length != 1)
                throw new RuntimeException("Failed to find Job: " + job_id);
            if (! "RUNNING".equals(jobs.jobs[0].status)) try { Thread.sleep(100); } catch (InterruptedException e) {} // wait 100mS
        } while ("RUNNING".equals(jobs.jobs[0].status));
        return jobs.jobs[0];
    }

    public static void gbm_example_flow() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(KeyV3.class, new KeySerializer());
//        builder.registerTypeAdapter(ColSpecifierV3.class, new ColSpecifierSerializer());
        Gson gson = builder.create();

        Retrofit retrofit = new Retrofit.Builder()
        .baseUrl("http://localhost:54321/") // note trailing slash for Retrofit 2
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build();

        ImportFiles importService = retrofit.create(ImportFiles.class);
        ParseSetup parseSetupService = retrofit.create(ParseSetup.class);
        Parse parseService = retrofit.create(Parse.class);
        Frames framesService = retrofit.create(Frames.class);
        Models modelsService = retrofit.create(Models.class);
        ModelBuilders modelBuildersService = retrofit.create(ModelBuilders.class);
        Predictions predictionsService = retrofit.create(Predictions.class);

        JobV3 job = null;

        try {
            // STEP 1: import raw file
            ImportFilesV3 importBody = importService.importFiles("http://s3.amazonaws.com/h2o-public-test-data/smalldata/flow_examples/arrhythmia.csv.gz", null, null).execute().body();
            System.out.println("import: " + importBody);

            // STEP 2: parse setup
            ParseSetupV3 parseSetupBody = parseSetupService.guessSetup(importBody.destinationFrames,
                                                                  ApiParseTypeValuesProvider.GUESS,
                                                                  (byte)',',
                                                                  false,
                                                                  -1,
                                                                  null,
                                                                  null,
                                                                  null,
                                                                  null,
                                                                  null,
                                                                  0,
                                                                  0,
                                                                  0,
                                                                  null,
                                                                  null).execute().body();
            System.out.println("parseSetupBody: " + parseSetupBody);

            // STEP 3: parse into columnar Frame
            List<String> source_frames = new ArrayList<>();
            for (FrameKeyV3 frame : parseSetupBody.sourceFrames)
              source_frames.add(frame.name);

            ParseV3 parseBody = parseService.parse("arrhythmia.hex",
                                                   source_frames.toArray(new String[0]),
                                                   parseSetupBody.parseType,
                                                   parseSetupBody.separator,
                                                   parseSetupBody.singleQuotes,
                                                   parseSetupBody.checkHeader,
                                                   parseSetupBody.numberColumns,
                                                   parseSetupBody.columnNames,
                                                   parseSetupBody.columnTypes,
                                                   parseSetupBody.skippedColumns,
                                                   null, // domains
                                                   parseSetupBody.naStrings,
                                                   parseSetupBody.chunkSize,
                                                   true,
                                                   true,
                                                   null, null).execute().body();
            System.out.println("parseBody: " + parseBody);

            // STEP 5: Train the model (NOTE: step 4 is polling, which we don't require because we specified blocking for the parse above)
            GBMParametersV3 gbmParms = new GBMParametersV3();

            FrameKeyV3 trainingFrame = new FrameKeyV3();
            trainingFrame.name = "arrhythmia.hex";

            gbmParms.trainingFrame = trainingFrame;

            ColSpecifierV3 responseColumn = new ColSpecifierV3();
            responseColumn.columnName = "C1";
            gbmParms.responseColumn = responseColumn;

            System.out.println("About to train GBM. . .");
            GBMV3 gbmBody = (GBMV3)ModelBuilders.Helper.trainGbm(modelBuildersService, gbmParms).execute().body();
            System.out.println("gbmBody: " + gbmBody);

            // STEP 6: poll for completion
            job = gbmBody.job;
            if (null == job || null == job.key)
                throw new RuntimeException("train_gbm returned a bad Job: " + job);

            job = poll(retrofit, job.key.name);
            System.out.println("GBM build done.");

            // STEP 7: fetch the model
            // TODO: Retrofit seems to be only deserializing the base class.  What to do?
            KeyV3 modelKey = job.dest;
            ModelsV3 models = modelsService.fetch(modelKey.name).execute().body();
            System.out.println("models: " + models);
            // GBMModelV3 model = (GBMModelV3)models.models[0];
            // System.out.println("new GBM model: " + model);
            System.out.println("new GBM model: " + models.models[0]);

            // STEP 8: predict!
            ModelMetricsListSchemaV3 predictions = predictionsService.predict(modelKey.name,
                                                                              trainingFrame.name,
                                                                              "predictions",
                                                                              null,
                                                                              false, false, -1, null,
                                                                              false, false, false, false, null,
                                                                              false, -1, false, null, null).execute().body();
            System.out.println("predictions: " + predictions);

        }
        catch (IOException e) {
            System.err.println("Caught exception: " + e);
        }
    }

    public static void simple_example() {
        Gson gson = new GsonBuilder().registerTypeAdapter(KeyV3.class, new KeySerializer()).create();

        Retrofit retrofit = new Retrofit.Builder()
        .baseUrl("http://localhost:54321/") // note trailing slash for Retrofit 2
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build();

        CreateFrame createFrameService = retrofit.create(CreateFrame.class);
        Frames framesService = retrofit.create(Frames.class);
        Models modelsService = retrofit.create(Models.class);

        try {
            // NOTE: the Call objects returned by the service can't be reused, but they can be cloned.
            Response<FramesListV3> all_frames_response = framesService.list().execute();
            Response<ModelsV3> all_models_response = modelsService.list().execute();

            if (all_frames_response.isSuccessful()) {
                FramesListV3 all_frames = all_frames_response.body();
                System.out.println("All Frames: ");
                System.out.println(all_frames);
            } else {
                System.err.println("framesService.list() failed");
            }
            if (all_models_response.isSuccessful()) {
                ModelsV3 all_models = all_models_response.body();
                System.out.println("All Models: ");
                System.out.println(all_models);
            } else {
                System.err.println("modelsService.list() failed");
            }

            Response<JobV3> create_frame_response = createFrameService.run(null, 1000, 100, 42, 42, true, 0, 100000, 0.2, 100, 0.2, 32767, 0.2, 0.5, 0.2, 0, 0.2, true, 2, true, null).execute();
            if (create_frame_response.isSuccessful()) {
                JobV3 job = create_frame_response.body();

                if (null == job || null == job.key)
                    throw new RuntimeException("CreateFrame returned a bad Job: " + job);

                job = poll(retrofit, job.key.name);

                KeyV3 new_frame = job.dest;
                System.out.println("Created frame: " + new_frame);

                all_frames_response = framesService.list().execute();
                if (all_frames_response.isSuccessful()) {
                    FramesListV3 all_frames = all_frames_response.body();
                    System.out.println("All Frames (after createFrame): ");
                    System.out.println(all_frames);
                } else {
                    System.err.println("framesService.list() failed");
                }

                Response<FramesV3> one_frame_response = framesService.fetch(new_frame.name).execute();
                if (one_frame_response.isSuccessful()) {
                    FramesV3 one_frames = one_frame_response.body();
                    System.out.println("One Frame (after createFrame): ");
                    System.out.println(one_frames);
                } else {
                    System.err.println("framesService.fetch() failed");
                }

            } else {
                System.err.println("createFrameService.run() failed");
            }
        }
        catch (IOException e) {
            System.err.println("Caught exception: " + e);
        }
    } // simple_example()

    public static void main (String[] args) {
        gbm_example_flow();
        simple_example();
    }
}

