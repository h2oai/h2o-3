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
#' Map of binary operators to their "AST" operator value.
#'
.op.map <- c("%*%" = "x",
             '>'  = '>',
             '>=' = '>=',
             '<'  = '<',
             '<=' = '<=',
             '==' = '==',
             '!=' = '!=',
             '%%' = 'mod',
             '**' = '^',
             '!'  = '_',
             '|'  = '|',
             '&'  = '&',
             "&&" = "&&",
             '+'  = '+',
             '-'  = '-',
             '*'  = '*',
             '/'  = '/',
             '^'  = '^',
             't'  = 't', 
             "%/%"="intDiv")

.binary_op.map <- c("%*%" = "x",
                    '>'  = '>',
                    '>=' = '>=',
                    '<'  = '<',
                    '<=' = '<=',
                    '==' = '==',
                    '!=' = '!=',
                    '%%' = '%',
                    '**' = '^',
                    '|'  = '|',
                    '&'  = '&',
                    "&&" = "&&",
                    '+'  = '+',
                    '-'  = '-',
                    '*'  = '*',
                    '/'  = '/',
                    '^'  = '^',
                    "%/%"="%/%")

#'
#' Map of unary operators to their "AST" operator value.
#'
.unary_op.map <- c('!' = '_',
                   '$' = '[',
                   '[' = '[',
                   't' = 't')

.slice.map <- c('$' = '$', '[' = '[')

#'
#' A list of prefix operations. 1:1 map for now, may add more to the map as needed.
#'
.prefix.map <-  c("abs"  = "abs",
                  "sign" = "sign",
                  "sqrt" = "sqrt",
                  "ceiling" = "ceiling",
                  "floor" = "floor",
                  "trunc" = "trunc",
                  "cummax" = "cummax",
                  "cummin" = "cummin",
                  "cumprod" = "cumprod",
                  "cumsum" = "cumsum",
                  "log" = "log",
                  "log10" = "log10",
                  "log2" = "log2",
                  "log1p" = "log1p",
                  "acos" = "acos",
                  "acosh" = "acosh",
                  "asin" = "asin",
                  "asinh" = "asinh",
                  "atan" = "atan",
                  "atanh" = "atanh",
                  "exp" = "exp",
                  "expm1" = "expm1",
                  "cos" = "cos",
                  "cosh" = "cosh",
                  "cospi" = "cospi",
                  "sin" = "sin",
                  "sinh" = "sinh",
                  "sinpi" = "sinpi",
                  "tan" = "tan",
                  "tanh" = "tanh",
                  "tanpi" = "tanpi",
                  "gamma" = "gamma",
                  "lgamma" = "lgamma",
                  "digamma" = "digamma",
                  "trigamma" = "trigamma",
                  "round" = "round",
                  "signif" = "signif",
                  "max" = "max",
                  "nrow"="nrow",
                  "ncol"="ncol",
                  "min" = "min",
                  "range" = "range",
                  "prod" = "prod",
                  "sum" = "sum",
                  "any" = "any",
                  "all" = "all",
                  "is.na" = "is.na",
                  "trunc" = "trunc",
                  "is.factor" = "is.factor",
                  "h2o.which"     = "which")

#'
#' The n-ary args operations
#'
.nary_op.map <- c("round" = "round",
                  "signif" = "signif",
                  "max" = "max",
                  "min" = "min",
                  "range" = "range",
                  "prod" = "prod",
                  "sum" = "sum",
                  "any" = "any",
                  "all" = "all",
                  "mean"  = "mean",
                  "median" = "median",
                  "var"   = "var",
                  "log" = "log",
                  "sd"    = "sd",
                  "scale" = "scale",
                  "tail" = "tail",
                  "head" = "head",
                  "match" = "match",
                  "cut" = "cut",
                  "table" = "table",
                  "xorsum" = "xorsum",
                  "trunc" = "trunc")

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
                   data.frame(type = "character", scalar = TRUE,  row.names = "Key<Key>",     stringsAsFactors = FALSE),
                   data.frame(type = "H2OModel",  scalar = TRUE,  row.names = "Key<Model>",   stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = TRUE,  row.names = "int",          stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = FALSE, row.names = "int[]",        stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = TRUE,  row.names = "long",         stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = FALSE, row.names = "long[]",       stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = TRUE,  row.names = "string",       stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = FALSE, row.names = "string[]",     stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = TRUE,  row.names = "VecSpecifier", stringsAsFactors = FALSE))

#' Administrative Endpoints
.h2o.__JOBS           <- "Jobs"          # Jobs/$90w3r52hfej_JOB_KEY_12389471jsdfs
.h2o.__CLOUD          <- "Cloud?skip_ticks=true"
.h2o.__SHUTDOWN       <- "Shutdown"
.h2o.__DOWNLOAD_LOGS  <- "/Logs/download"

#' Removal Endpoints
.h2o.__DKV            <- "DKV"

#' Log and Echo Endpoint
.h2o.__LOGANDECHO     <- "LogAndEcho"

#' Import/Export Endpoints
.h2o.__IMPORT         <- "ImportFiles"   # ImportFiles?path=/path/to/data

#' Parse Endpoints
.h2o.__PARSE_SETUP    <- "ParseSetup"    # Sample Usage: ParseSetup?source_keys=["nfs://asdfsdf...", "nfs://..."]
.h2o.__PARSE          <- "Parse"         # Sample Usage: Parse?source_keys=["nfs://path/to/data"]&destination_frame=KEYNAME&parse_type=CSV&separator=44&number_columns=5&check_header=0&single_quotes=false&column_names=["C1",%20"C2",%20"C3",%20"C4",%20"C5"]

#' Inspect/Summary Endpoints
.h2o.__FRAMES         <- "Frames"        # Frames/<key>    example: http://localhost:54321/3/Frames/meow.hex
.h2o.__COL_SUMMARY <- function(key, col) URLencode(paste(.h2o.__FRAMES, key, "columns", col, "summary", sep = "/"))
.h2o.__COL_DOMAIN  <- function(key, col) URLencode(paste(.h2o.__FRAMES, key, "columns", col, "domain", sep = "/"))

#' Frame Manipulation
.h2o.__CREATE_FRAME   <- "CreateFrame"

.h2o.__GLMMakeModel <- "MakeGLMModel"

#' Rapids Endpoint
.h2o.__RAPIDS         <- "Rapids"

#' Model Builder Endpoint Generator
.h2o.__MODEL_BUILDERS <- function(algo) paste0("ModelBuilders/", algo)

#' Export Files Endpoint Generator
.h2o.__EXPORT_FILES <- function(frame,path,force) {
  .h2o.eval.frame(frame)
  paste0("Frames/",frame@id,"/export/",path,"/overwrite/",force)
}

#' Model Endpoint
.h2o.__MODELS         <- "Models"

#' Word To Vector Endpoints
.h2o.__W2V            <- "Word2Vec"
.h2o.__SYNONYMS       <- "Synonyms"
.h2o.__TRANSFORM      <- "Transform"

#' Model Metrics Endpoint
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


