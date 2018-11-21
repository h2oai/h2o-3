#'
#' H2O Package Constants
#'
#' The API endpoints for interacting with H2O via REST are named here.
#'
#' Additionally, environment variables for the H2O package are named here.

#' Endpoint Version
.h2o.__REST_API_VERSION <- 3L

#'
#' The H2O Package Environment
#'
.pkg.env <- new.env()
.pkg.env$key.map <- list()   # global list of versioned keys
assign("SERVER",        NULL,  .pkg.env)
assign("IS_LOGGING",    FALSE, .pkg.env)
assign("LOG_FILE_NAME", NULL,  .pkg.env)

#'
#' Map of operations known to H2O
#'
.h2o.primitives = c( 
  "*", "+", "/", "-", "^", "%%", "%/%",
  "==", "!=", "<", ">", "<=", ">=",
  "cos", "sin", "acos", "cosh", "tan", "tanh", "exp", "log", "sqrt", 
  "abs", "ceiling", "floor", 
  "mean", "sd", "sum", "prod", "all", "any", "min", "max", 
  "is.factor", "nrow", "ncol", "length" , "which.max", "which.min"
)


.type.map <- rbind(data.frame(type = "logical",   scalar = TRUE,  row.names = "boolean",      stringsAsFactors = FALSE),
                   data.frame(type = "logical",   scalar = FALSE, row.names = "boolean[]",    stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = TRUE,  row.names = "enum",         stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = FALSE, row.names = "enum[]",       stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = TRUE,  row.names = "double",       stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = FALSE, row.names = "double[]",     stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = TRUE,  row.names = "float",        stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = FALSE, row.names = "float[]",      stringsAsFactors = FALSE),
                   data.frame(type = "H2OFrame",  scalar = TRUE,  row.names = "Key",          stringsAsFactors = FALSE),
                   data.frame(type = "H2OFrame",  scalar = TRUE,  row.names = "Key<Frame>",   stringsAsFactors = FALSE),
                   data.frame(type = "list",      scalar = FALSE, row.names = "Key<Frame>[]", stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = TRUE,  row.names = "Key<Key>",     stringsAsFactors = FALSE),
                   data.frame(type = "H2OModel",  scalar = TRUE,  row.names = "Key<Model>",   stringsAsFactors = FALSE),
                   data.frame(type = "list",      scalar = FALSE,  row.names = "Key<Model>[]", stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = TRUE,  row.names = "int",          stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = FALSE, row.names = "int[]",        stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = TRUE,  row.names = "long",         stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = FALSE, row.names = "long[]",       stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = TRUE,  row.names = "string",       stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = FALSE, row.names = "string[]",     stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = TRUE,  row.names = "VecSpecifier", stringsAsFactors = FALSE),
                   data.frame(type = "list",      scalar = FALSE, row.names = "KeyValue[]",   stringsAsFactors = FALSE),
                   data.frame(type = "list",      scalar = FALSE, row.names = "StringPair[]", stringsAsFactors = FALSE))

#' Capabilities endpoints
.h2o.__ALL_CAPABILITIES  <- "Capabilities"
.h2o.__CORE_CAPABILITIES <- "Capabilities/Core"
.h2o.__API_CAPABILITIES  <- "Capabilities/API"

#' Administrative Endpoints
.h2o.__JOBS           <- "Jobs"          # Jobs/$90w3r52hfej_JOB_KEY_12389471jsdfs
.h2o.__CLOUD          <- "Cloud?skip_ticks=true"
.h2o.__SHUTDOWN       <- "Shutdown"
.h2o.__DOWNLOAD_LOGS  <- "3/Logs/download"

#' Removal Endpoints
.h2o.__DKV            <- "DKV"

#' Log and Echo Endpoint
.h2o.__LOGANDECHO     <- "LogAndEcho"

#' Import/Export Endpoints
.h2o.__IMPORT         <- "ImportFiles"   # ImportFiles?path=/path/to/data

#' Parse Endpoints
.h2o.__PARSE_SETUP    <- "ParseSetup"    # Sample Usage: ParseSetup?source_keys=["nfs://asdfsdf...", "nfs://..."]
.h2o.__PARSE          <- "Parse"         # Sample Usage: Parse?source_keys=["nfs://path/to/data"]&destination_frame=KEYNAME&parse_type=CSV&separator=44&number_columns=5&check_header=0&single_quotes=false&column_names=["C1",%20"C2",%20"C3",%20"C4",%20"C5"]
.h2o.__PARSE_SVMLIGHT <- "ParseSVMLight" # Sample Usage: Parse?source_keys=["nfs://path/to/data"]&destination_frame=KEYNAME

#' Decryption Endpoints
.h2o.__DECRYPTION_SETUP <- "DecryptionSetup"

#' Inspect/Summary Endpoints
.h2o.__FRAMES         <- "Frames"        # Frames/<key>    example: http://localhost:54321/3/Frames/meow.hex
.h2o.__COL_SUMMARY <- function(key, col) URLencode(paste(.h2o.__FRAMES, key, "columns", col, "summary", sep = "/"))
.h2o.__COL_DOMAIN  <- function(key, col) URLencode(paste(.h2o.__FRAMES, key, "columns", col, "domain", sep = "/"))

#' H2OFrame Manipulation
.h2o.__CREATE_FRAME   <- "CreateFrame"

.h2o.__GLMMakeModel <- "MakeGLMModel"
.h2o.__GLMRegPath <- "GetGLMRegPath"
.h2o.__ComputeGram <- "ComputeGram"


#' Rapids Endpoint
.h2o.__RAPIDS         <- "Rapids"

#' Model Builder Endpoint Generator
#'
#' @param algo Cannonical identifier of H2O algorithm.
.h2o.__MODEL_BUILDERS <- function(algo) paste0("ModelBuilders/", algo)

#' Export Files Endpoint Generator
#'
#' @param frame H2OFrame
.h2o.__EXPORT_FILES <- function(frame) paste0("Frames/", h2o.getId(frame), '/export')

#' Model Endpoint
.h2o.__MODELS         <- "Models"

#' Word2Vec Endpoints
.h2o.__W2V_SYNONYMS       <- "Word2VecSynonyms"

#' Model Metrics Endpoint
#'
#' @param model H2OModel.
#' @param data H2OFrame.
.h2o.__MODEL_METRICS <- function(model,data) {
  if(missing(data)) {
    paste0("ModelMetrics/models/",model)
  }
  else {
    paste0("ModelMetrics/models/",model,"/frames/",data)
  }
}

# Export/Import Model Endpoints
.h2o.__SAVE_MODEL <- function(model) paste0("Models.bin/", model)
.h2o.__LOAD_MODEL <- "Models.bin/"

# Grid search 
.h2o.__GRID <- function(algo) paste0("Grid/", algo)
.h2o.__GRIDS <- function(grid_id, sort_by, decreasing) {
    if (missing(grid_id)) {
        "/Grids"
    } else {
        url <- paste0("Grids/", grid_id)
        if (! missing(sort_by)) {
            url <- paste0(url, "?sort_by=", sort_by)
            if (! missing(decreasing)) {
                url <- paste0(url, "&decreasing=", decreasing)
            }
        }
         url
    }
}
