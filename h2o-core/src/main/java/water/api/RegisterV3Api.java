package water.api;

/**
 *
 */
public class RegisterV3Api extends AbstractRegister {

  @Override
  public void registerEndPoints(RestApiContext context) {
    // Data
    context.registerEndpoint("createFrame",
            "POST /3/CreateFrame", CreateFrameHandler.class, "run",
            "Create a synthetic H2O Frame with random data. You can specify the number of rows/columns, as well as column" +
                    " types: integer, real, boolean, time, string, categorical. The frame may also have a dedicated \"response\" " +
                    "column, and some of the entries in the dataset may be created as missing.");

    context.registerEndpoint("splitFrame",
            "POST /3/SplitFrame", SplitFrameHandler.class, "run",
            "Split an H2O Frame.");

    context.registerEndpoint("generateInteractions",
            "POST /3/Interaction", InteractionHandler.class, "run",
            "Create interactions between categorical columns.");

    context.registerEndpoint("_missingInserter_run",
            "POST /3/MissingInserter", MissingInserterHandler.class, "run",
            "Insert missing values.");

    context.registerEndpoint("_dctTransformer_run",
            "POST /99/DCTTransformer", DCTTransformerHandler.class, "run",
            "Row-by-row discrete cosine transforms in 1D, 2D and 3D.");

    context.registerEndpoint("_tabulate_run",
            "POST /99/Tabulate", TabulateHandler.class, "run",
            "Tabulate one column vs another.");

    context.registerEndpoint("importFiles_deprecated",
            "GET /3/ImportFiles", ImportFilesHandler.class, "importFiles",
            "[DEPRECATED] Import raw data files into a single-column H2O Frame.");

    context.registerEndpoint("importFiles",
            "POST /3/ImportFiles", ImportFilesHandler.class, "importFiles",
            "Import raw data files into a single-column H2O Frame.");

    context.registerEndpoint("importFilesMulti",
            "POST /3/ImportFilesMulti", ImportFilesHandler.class, "importFilesMulti",
            "Import raw data files from multiple directories (or different data sources) into a single-column H2O Frame.");

    context.registerEndpoint("importSqlTable",
            "POST /99/ImportSQLTable", ImportSQLTableHandler.class, "importSQLTable",
            "Import SQL table into an H2O Frame.");

    context.registerEndpoint("importHiveTable",
        "POST /99/ImportHiveTable", ImportHiveTableHandler.class, "importHiveTable",
        "Import Hive table into an H2O Frame.");

    context.registerEndpoint("guessParseSetup",
            "POST /3/ParseSetup", ParseSetupHandler.class, "guessSetup",
            "Guess the parameters for parsing raw byte-oriented data into an H2O Frame.");

    context.registerEndpoint("parse",
            "POST /3/Parse", ParseHandler.class, "parse",
            "Parse a raw byte-oriented Frame into a useful columnar data Frame."); // NOTE: prefer POST due to higher content limits

    context.registerEndpoint("setupDecryption",
            "POST /3/DecryptionSetup", DecryptionSetupHandler.class, "setupDecryption",
            "Install a decryption tool for parsing of encrypted data.");

    context.registerEndpoint("parseSvmLight",
            "POST /3/ParseSVMLight", ParseHandler.class, "parseSVMLight",
            "Parse a raw byte-oriented Frame into a useful columnar data Frame."); // NOTE: prefer POST due to higher content limits

    // Admin
    context.registerEndpoint("cloudStatus",
            "GET /3/Cloud", CloudHandler.class, "status",
            "Determine the status of the nodes in the H2O cloud.");

    context.registerEndpoint("cloudStatusMinimal",
            "HEAD /3/Cloud", CloudHandler.class, "head",
            "Determine the status of the nodes in the H2O cloud.");

    context.registerEndpoint("jobs",
            "GET /3/Jobs", JobsHandler.class, "list",
            "Get a list of all the H2O Jobs (long-running actions).");

    context.registerEndpoint("timeline",
            "GET /3/Timeline", TimelineHandler.class, "fetch",
            "Debugging tool that provides information on current communication between nodes.");

    context.registerEndpoint("profiler",
            "GET /3/Profiler", ProfilerHandler.class, "fetch",
            "Report real-time profiling information for all nodes (sorted, aggregated stack traces).");

    context.registerEndpoint("stacktraces",
            "GET /3/JStack", JStackHandler.class, "fetch",
            "Report stack traces for all threads on all nodes.");

    context.registerEndpoint("testNetwork",
            "GET /3/NetworkTest", NetworkTestHandler.class, "fetch",
            "Run a network test to measure the performance of the cluster interconnect.");

    context.registerEndpoint("unlockAllKeys",
            "POST /3/UnlockKeys", UnlockKeysHandler.class, "unlock",
            "Unlock all keys in the H2O distributed K/V store, to attempt to recover from a crash.");

    context.registerEndpoint("shutdownCluster",
            "POST /3/Shutdown", ShutdownHandler.class, "shutdown",
            "Shut down the cluster.");

    // REST only, no html:
    context.registerEndpoint("about",
            "GET /3/About", AboutHandler.class, "get",
            "Return information about this H2O cluster.");

    context.registerEndpoint("endpoints",
            "GET /3/Metadata/endpoints", MetadataHandler.class, "listRoutes",
            "Return the list of (almost) all REST API endpoints.");

    context.registerEndpoint("endpoint",
            "GET /3/Metadata/endpoints/{path}", MetadataHandler.class, "fetchRoute",
            "Return the REST API endpoint metadata, including documentation, for the endpoint specified by path or index.");

    context.registerEndpoint("schemaForClass",
            "GET /3/Metadata/schemaclasses/{classname}", MetadataHandler.class, "fetchSchemaMetadataByClass",
            "Return the REST API schema metadata for specified schema class.");

    context.registerEndpoint("schema",
            "GET /3/Metadata/schemas/{schemaname}", MetadataHandler.class, "fetchSchemaMetadata",
            "Return the REST API schema metadata for specified schema.");

    context.registerEndpoint("schemas",
            "GET /3/Metadata/schemas", MetadataHandler.class, "listSchemas",
            "Return list of all REST API schemas.");

    context.registerEndpoint("typeaheadFileSuggestions",
            "GET /3/Typeahead/files", TypeaheadHandler.class, "files",
            "Typeahead hander for filename completion.");

    context.registerEndpoint("job",
            "GET /3/Jobs/{job_id}", JobsHandler.class, "fetch",
            "Get the status of the given H2O Job (long-running action).");

    context.registerEndpoint("cancelJob",
            "POST /3/Jobs/{job_id}/cancel", JobsHandler.class, "cancel",
            "Cancel a running job.");

    context.registerEndpoint("findInFrame",
            "GET /3/Find", FindHandler.class, "find",
            "Find a value within a Frame.");

    context.registerEndpoint("exportFrame_deprecated",
            "GET /3/Frames/{frame_id}/export/{path}/overwrite/{force}", FramesHandler.class, "export",
            "[DEPRECATED] Export a Frame to the given path with optional overwrite.");

    context.registerEndpoint("exportFrame",
            "POST /3/Frames/{frame_id}/export", FramesHandler.class, "export",
            "Export a Frame to the given path with optional overwrite.");

    context.registerEndpoint("frameColumnSummary",
            "GET /3/Frames/{frame_id}/columns/{column}/summary", FramesHandler.class, "columnSummary",
            "Return the summary metrics for a column, e.g. min, max, mean, sigma, percentiles, etc.");

    context.registerEndpoint("frameColumnDomain",
            "GET /3/Frames/{frame_id}/columns/{column}/domain", FramesHandler.class, "columnDomain",
            "Return the domains for the specified categorical column (\"null\" if the column is not a categorical).");

    context.registerEndpoint("frameColumn",
            "GET /3/Frames/{frame_id}/columns/{column}", FramesHandler.class, "column",
            "Return the specified column from a Frame.");

    context.registerEndpoint("frameColumns",
            "GET /3/Frames/{frame_id}/columns", FramesHandler.class, "columns",
            "Return all the columns from a Frame.");

    context.registerEndpoint("frameSummary",
            "GET /3/Frames/{frame_id}/summary", FramesHandler.class, "summary",
            "Return a Frame, including the histograms, after forcing computation of rollups.");

    context.registerEndpoint("lightFrame",
            "GET /3/Frames/{frame_id}/light", FramesHandler.class, "fetchLight",
            "Return a basic info about Frame to fill client Rapid expression cache.");

    context.registerEndpoint("frame",
            "GET /3/Frames/{frame_id}", FramesHandler.class, "fetch",
            "Return the specified Frame.");

    context.registerEndpoint("frames",
            "GET /3/Frames", FramesHandler.class, "list",
            "Return all Frames in the H2O distributed K/V store.");

    context.registerEndpoint("deleteFrame",
            "DELETE /3/Frames/{frame_id}", FramesHandler.class, "delete",
            "Delete the specified Frame from the H2O distributed K/V store.");

    context.registerEndpoint("deleteAllFrames",
            "DELETE /3/Frames", FramesHandler.class, "deleteAll",
            "Delete all Frames from the H2O distributed K/V store.");


    // Handle models
    context.registerEndpoint("model",
            "GET /3/Models/{model_id}", ModelsHandler.class, "fetch",
            "Return the specified Model from the H2O distributed K/V store, optionally with the list of compatible Frames.");

    context.registerEndpoint("models",
            "GET /3/Models", ModelsHandler.class, "list",
            "Return all Models from the H2O distributed K/V store.");

    context.registerEndpoint("deleteModel",
            "DELETE /3/Models/{model_id}", ModelsHandler.class, "delete",
            "Delete the specified Model from the H2O distributed K/V store.");

    context.registerEndpoint("deleteAllModels",
            "DELETE /3/Models", ModelsHandler.class, "deleteAll",
            "Delete all Models from the H2O distributed K/V store.");

    // Get java code for models as
    context.registerEndpoint("modelPreview",
            "GET /3/Models.java/{model_id}/preview", ModelsHandler.class, "fetchPreview",
            "Return potentially abridged model suitable for viewing in a browser (currently only used for java model code).");

    // Register resource also with .java suffix since we do not want to break API
    context.registerEndpoint("modelJavaCode",
            "GET /3/Models.java/{model_id}", ModelsHandler.class, "fetchJavaCode",
            "[DEPRECATED] Return the stream containing model implementation in Java code.");

    context.registerEndpoint("modelMojo",
            "GET /3/Models/{model_id}/mojo", ModelsHandler.class, "fetchMojo",
            "Return the model in the MOJO format. This format can then be interpreted by " +
                    "gen_model.jar in order to perform prediction / scoring. Currently works for GBM and DRF algos only.");

    context.registerEndpoint("makePDP",
            "POST /3/PartialDependence/", ModelsHandler.class, "makePartialDependence",
            "Create data for partial dependence plot(s) for the specified model and frame.");

    context.registerEndpoint("fetchPDP",
            "GET /3/PartialDependence/{name}", ModelsHandler.class, "fetchPartialDependence",
            "Fetch partial dependence data.");

    // Model serialization - import/export calls
    context.registerEndpoint("importModel",
            "POST /99/Models.bin/{model_id}", ModelsHandler.class, "importModel",
            "Import given binary model into H2O.");

    context.registerEndpoint("exportModel",
            "GET /99/Models.bin/{model_id}", ModelsHandler.class, "exportModel",
            "Export given model.");

    context.registerEndpoint("exportMojo",
            "GET /99/Models.mojo/{model_id}", ModelsHandler.class, "exportMojo",
            "Export given model as Mojo.");

    context.registerEndpoint("exportModelDetails",
            "GET /99/Models/{model_id}/json", ModelsHandler.class, "exportModelDetails",
            "Export given model details in json format.");

    context.registerEndpoint("grid",
            "GET /99/Grids/{grid_id}", GridsHandler.class, "fetch",
            "Return the specified grid search result.");

    context.registerEndpoint("grids",
            "GET /99/Grids", GridsHandler.class, "list",
            "Return all grids from H2O distributed K/V store.");

    context.registerEndpoint("newModelId",
            "POST /3/ModelBuilders/{algo}/model_id", ModelBuildersHandler.class, "calcModelId",
            "Return a new unique model_id for the specified algorithm.");

    context.registerEndpoint("modelBuilder",
            "GET /3/ModelBuilders/{algo}", ModelBuildersHandler.class, "fetch",
            "Return the Model Builder metadata for the specified algorithm.");

    context.registerEndpoint("modelBuilders",
            "GET /3/ModelBuilders", ModelBuildersHandler.class, "list",
            "Return the Model Builder metadata for all available algorithms.");


    // TODO: filtering isn't working for these first four; we get all results:
    context.registerEndpoint("_mmFetch1",
            "GET /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "fetch",
            "Return the saved scoring metrics for the specified Model and Frame.");

    context.registerEndpoint("_mmDelete1",
            "DELETE /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "delete",
            "Return the saved scoring metrics for the specified Model and Frame.");

    context.registerEndpoint("_mmFetch2",
            "GET /3/ModelMetrics/models/{model}", ModelMetricsHandler.class, "fetch",
            "Return the saved scoring metrics for the specified Model.");

    context.registerEndpoint("_mmFetch3",
            "GET /3/ModelMetrics/frames/{frame}/models/{model}", ModelMetricsHandler.class, "fetch",
            "Return the saved scoring metrics for the specified Model and Frame.");

    context.registerEndpoint("_mmDelete2",
            "DELETE /3/ModelMetrics/frames/{frame}/models/{model}", ModelMetricsHandler.class, "delete",
            "Return the saved scoring metrics for the specified Model and Frame.");

    context.registerEndpoint("_mmFetch4",
            "GET /3/ModelMetrics/frames/{frame}", ModelMetricsHandler.class, "fetch",
            "Return the saved scoring metrics for the specified Frame.");

    context.registerEndpoint("_mmFetch5",
            "GET /3/ModelMetrics", ModelMetricsHandler.class, "fetch",
            "Return all the saved scoring metrics.");

    context.registerEndpoint("score",
            "POST /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "score",
            "Return the scoring metrics for the specified Frame with the specified Model.  If the Frame has already been " +
                    "scored with the Model then cached results will be returned; otherwise predictions for all rows in the Frame " +
                    "will be generated and the metrics will be returned.");

    context.registerEndpoint("predict",
            "POST /3/Predictions/models/{model}/frames/{frame}", ModelMetricsHandler.class, "predict",
            "Score (generate predictions) for the specified Frame with the specified Model.  Both the Frame of " +
                    "predictions and the metrics will be returned.");

    context.registerEndpoint("predict_async",
            "POST /4/Predictions/models/{model}/frames/{frame}", ModelMetricsHandler.class, "predictAsync",
            "Score (generate predictions) for the specified Frame with the specified Model.  Both the Frame of " +
                    "predictions and the metrics will be returned.");

    context.registerEndpoint("makeMetrics",
            "POST /3/ModelMetrics/predictions_frame/{predictions_frame}/actuals_frame/{actuals_frame}", ModelMetricsHandler.class, "make",
            "Create a ModelMetrics object from the predicted and actual values, and a domain for classification problems or a distribution family for regression problems.");

    context.registerEndpoint("waterMeterCpuTicks",
            "GET /3/WaterMeterCpuTicks/{nodeidx}", WaterMeterCpuTicksHandler.class, "fetch",
            "Return a CPU usage snapshot of all cores of all nodes in the H2O cluster.");

    context.registerEndpoint("waterMeterIoForNode",
            "GET /3/WaterMeterIo/{nodeidx}", WaterMeterIoHandler.class, "fetch",
            "Return IO usage snapshot of all nodes in the H2O cluster.");

    context.registerEndpoint("waterMeterIoForCluster",
            "GET /3/WaterMeterIo", WaterMeterIoHandler.class, "fetch_all",
            "Return IO usage snapshot of all nodes in the H2O cluster.");

    // Node persistent storage
    context.registerEndpoint("npsContains",
            "GET /3/NodePersistentStorage/categories/{category}/names/{name}/exists",
            NodePersistentStorageHandler.class, "exists",
            "Return true or false.");

    context.registerEndpoint("npsExistsCategory",
            "GET /3/NodePersistentStorage/categories/{category}/exists", NodePersistentStorageHandler.class, "exists",
            "Return true or false.");

    context.registerEndpoint("npsEnabled",
            "GET /3/NodePersistentStorage/configured", NodePersistentStorageHandler.class, "configured",
            "Return true or false.");

    context.registerEndpoint("npsPut",
            "POST /3/NodePersistentStorage/{category}/{name}", NodePersistentStorageHandler.class, "put_with_name",
            "Store a named value.");

    context.registerEndpoint("npsGet",
            "GET /3/NodePersistentStorage/{category}/{name}", NodePersistentStorageHandler.class, "get_as_string",
            "Return value for a given name.");

    context.registerEndpoint("npsRemove",
            "DELETE /3/NodePersistentStorage/{category}/{name}", NodePersistentStorageHandler.class, "delete",
            "Delete a key.");

    context.registerEndpoint("npsCreateCategory",
            "POST /3/NodePersistentStorage/{category}", NodePersistentStorageHandler.class, "put",
            "Store a value.");

    context.registerEndpoint("npsKeys",
            "GET /3/NodePersistentStorage/{category}", NodePersistentStorageHandler.class, "list",
            "Return all keys stored for a given category.");

    // TODO: context.registerEndpoint("DELETE /3/ModelMetrics/models/{model}/frames/{frame}", ModelMetricsHandler.class, "delete");
    // TODO: context.registerEndpoint("DELETE /3/ModelMetrics/frames/{frame}/models/{model}", ModelMetricsHandler.class, "delete");
    // TODO: context.registerEndpoint("DELETE /3/ModelMetrics/frames/{frame}", ModelMetricsHandler.class, "delete");
    // TODO: context.registerEndpoint("DELETE /3/ModelMetrics/models/{model}", ModelMetricsHandler.class, "delete");
    // TODO: context.registerEndpoint("DELETE /3/ModelMetrics", ModelMetricsHandler.class, "delete");
    // TODO: context.registerEndpoint("POST /3/Predictions/models/{model}/frames/{frame}", ModelMetricsHandler.class, "predict");

    // Log file management.
    // Note:  Hacky pre-route cutout of "/3/Logs/download" is done above in a non-json way.
    context.registerEndpoint("logs",
            "GET /3/Logs/nodes/{nodeidx}/files/{name}", LogsHandler.class, "fetch",
            "Get named log file for a node.");


    // ModelBuilder Handler registration must be done for each algo in the application class
    // (e.g., H2OApp), because the Handler class is parameterized by the associated Schema,
    // and this is different for each ModelBuilder in order to handle its parameters in a
    // typesafe way:
    //   context.registerEndpoint("POST /3/ModelBuilders/{algo}", ModelBuildersHandler.class, "train", "Train {algo}");
    //

    context.registerEndpoint("logThreadDump",
            "GET /3/KillMinus3", KillMinus3Handler.class, "killm3",
            "Kill minus 3 on *this* node");

    context.registerEndpoint("rapidsExec",
            "POST /99/Rapids", RapidsHandler.class, "exec",
            "Execute an Rapids AstRoot.");

    context.registerEndpoint("_assembly_toJava",
            "GET /99/Assembly.java/{assembly_id}/{pojo_name}", AssemblyHandler.class, "toJava",
            "Generate a Java POJO from the Assembly");

    context.registerEndpoint("_assembly_fit",
            "POST /99/Assembly", AssemblyHandler.class, "fit",
            "Fit an assembly to an input frame");

    context.registerEndpoint("_downloadDataset_fetch",
            "GET /3/DownloadDataset", DownloadDataHandler.class, "fetch",
            "Download dataset as a CSV.");

    context.registerEndpoint("_downloadDataset_fetchStreaming",
            "GET /3/DownloadDataset.bin", DownloadDataHandler.class, "fetchStreaming",
            "Download dataset as a CSV.");

    context.registerEndpoint("deleteKey",
            "DELETE /3/DKV/{key}", RemoveHandler.class, "remove",
            "Remove an arbitrary key from the H2O distributed K/V store.");

    context.registerEndpoint("deleteAllKeys",
            "DELETE /3/DKV", RemoveAllHandler.class, "remove",
            "Remove all keys from the H2O distributed K/V store.");

    context.registerEndpoint("logAndEcho",
            "POST /3/LogAndEcho", LogAndEchoHandler.class, "echo",
            "Save a message to the H2O logfile.");

    context.registerEndpoint("newSession",
            "GET /3/InitID", RapidsHandler.class, "startSession",
            "Issue a new session ID.");

    context.registerEndpoint("endSession",
            "DELETE /3/InitID", RapidsHandler.class, "endSession",
            "End a session.");

    context.registerEndpoint("garbageCollect",
            "POST /3/GarbageCollect", GarbageCollectHandler.class, "gc",
            "Explicitly call System.gc().");

    context.registerEndpoint("_sample_status",
            "GET /99/Sample", CloudHandler.class, "status",
            "Example of an experimental endpoint.  Call via /EXPERIMENTAL/Sample.  Experimental endpoints can change at " +
                    "any moment.");

    context.registerEndpoint("rapids_help",
            "GET /99/Rapids/help", RapidsHandler.class, "genHelp",
            "Produce help for Rapids AstRoot language.");

    // Endpoints for Steam to manage H2O.
    context.registerEndpoint("steamMetrics",
            "GET /3/SteamMetrics", SteamMetricsHandler.class, "fetch",
            "Get metrics for Steam from H2O.");

    context.registerEndpoint("list_all_capabilities",
            "GET /3/Capabilities", CapabilitiesHandler.class, "listAll",
            "List of all registered capabilities");

    context.registerEndpoint("list_core_capabilities",
            "GET /3/Capabilities/Core", CapabilitiesHandler.class, "listCore",
            "List registered core capabilities");

    context.registerEndpoint("list_rest_capabilities",
            "GET /3/Capabilities/API", CapabilitiesHandler.class, "listRest",
            "List of all registered Rest API capabilities");
  }

  @Override
  public String getName() {
    return "Core V3";
  }
}
