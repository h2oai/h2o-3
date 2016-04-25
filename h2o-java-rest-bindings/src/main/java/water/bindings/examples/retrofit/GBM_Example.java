package water.bindings.proxies.retrofit;

import water.bindings.pojos.*;
import water.bindings.proxies.*;
import com.google.gson.*;
import retrofit2.*;
import retrofit2.http.*;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.Call;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.ArrayList;

public class GBM_Example {

    /**
     * Keys get sent as Strings and returned as objects also containing the type and URL,
     * so they need a custom GSON serializer.
     */
    private static class KeySerializer implements JsonSerializer<KeyV3> {
        public JsonElement serialize(KeyV3 key, Type typeOfKey, JsonSerializationContext context) {
            return new JsonPrimitive(key.name);
        }
    }

    public static JobV3 poll(Retrofit retrofit, String job_id) {
        Jobs jobsService = retrofit.create(Jobs.class);
        Response<JobsV3> jobs_response = null;

        int retries = 3;
        JobsV3 jobs = null;
        do {
            try {
                jobs_response = jobsService.fetch(job_id).execute();
            }
            catch (IOException e) {
                System.err.println("Caught exception: " + e);
            }
            if (! jobs_response.isSuccessful())
                if (retries-- > 0)
                   continue;
                else
                    throw new RuntimeException("/3/Jobs/{job_id} failed 3 times.");

            jobs = jobs_response.body();
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
            ImportFilesV3 importBody = importService.importFiles("http://s3.amazonaws.com/h2o-public-test-data/smalldata/flow_examples/arrhythmia.csv.gz", null).execute().body();
            System.out.println("import: " + importBody);

            // STEP 2: parse setup
            ParseSetupV3 parseSetupBody = parseSetupService.guessSetup(importBody.destination_frames,
                                                                  ParserParserType.GUESS,
                                                                  (byte)',',
                                                                  false,
                                                                  -1,
                                                                  null,
                                                                  null,
                                                                  null,
                                                                  null,
                                                                  0,
                                                                  0,
                                                                  0,
                                                                  null
                                                                  ).execute().body();
            System.out.println("parseSetupBody: " + parseSetupBody);

            // STEP 3: parse into columnar Frame
            List<String> source_frames = new ArrayList<>();
            for (FrameKeyV3 frame : parseSetupBody.source_frames)
              source_frames.add(frame.name);

            ParseV3 parseBody = parseService.parse("arrhythmia.hex",
                                                   source_frames.toArray(new String[0]),
                                                   parseSetupBody.parse_type,
                                                   parseSetupBody.separator,
                                                   parseSetupBody.single_quotes,
                                                   parseSetupBody.check_header,
                                                   parseSetupBody.number_columns,
                                                   parseSetupBody.column_names,
                                                   parseSetupBody.column_types,
                                                   null, // domains
                                                   parseSetupBody.na_strings,
                                                   parseSetupBody.chunk_size,
                                                   true,
                                                   true,
                                                   null).execute().body();
            System.out.println("parseBody: " + parseBody);

            // STEP 5: Train the model (NOTE: step 4 is polling, which we don't require because we specified blocking for the parse above)
            GBMParametersV3 gbm_parms = new GBMParametersV3();

            FrameKeyV3 training_frame = new FrameKeyV3();
            training_frame.name = "arrhythmia.hex";

            gbm_parms.training_frame = training_frame;

            ColSpecifierV3 response_column = new ColSpecifierV3();
            response_column.column_name = "C1";
            gbm_parms.response_column = response_column;

            System.out.println("About to train GBM. . .");
            GBMV3 gbmBody = (GBMV3)ModelBuilders.Helper.train_gbm(modelBuildersService, gbm_parms).execute().body();
            System.out.println("gbmBody: " + gbmBody);

            // STEP 6: poll for completion
            job = gbmBody.job;
            if (null == job || null == job.key)
                throw new RuntimeException("train_gbm returned a bad Job: " + job);

            job = poll(retrofit, job.key.name);
            System.out.println("GBM build done.");

            // STEP 7: fetch the model
            // TODO: Retrofit seems to be only deserializing the base class.  What to do?
            KeyV3 model_key = job.dest;
            ModelsV3 models = modelsService.fetch(model_key.name).execute().body();
            System.out.println("models: " + models);
            // GBMModelV3 model = (GBMModelV3)models.models[0];
            // System.out.println("new GBM model: " + model);
            System.out.println("new GBM model: " + models.models[0]);

            // STEP 8: predict!
            ModelMetricsListSchemaV3 predictions = predictionsService.predict(model_key.name,
                                                                              training_frame.name,
                                                                              "predictions",
                                                                              false, false, -1, false, false, false, false, -1, null).execute().body();
            System.out.println("predictions: " + predictions);

        }
        catch (IOException e) {
            System.err.println("Caught exception: " + e);
        }
    }

    public static void main (String[] args) {
        gbm_example_flow();
    }
}

