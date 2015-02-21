#'
#' H2O Package Constants
#'
#' The API endpoints for interacting with H2O via REST are named here.
#'
#' Additionally, environment variables for the H2O package are named here.


#'
#' The H2O Package Environment
#'
.pkg.env <- new.env()
assign("SERVER",        NULL,  .pkg.env)
assign("IS_LOGGING",    FALSE, .pkg.env)
assign("LOG_FILE_NAME", NULL,  .pkg.env)

#'
#' Map of binary operators to their "AST" operator value.
#'
.op.map <- c("%*%" = "x",
             '>'  = 'g',
             '>=' = 'G',
             '<'  = 'l',
             '<=' = 'L',
             '==' = 'n',
             '!=' = 'N',
             '%%' = '%',
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
             "%/%"="%/%")

.binary_op.map <- c("%*%" = "x",
                    '>'  = 'g',
                    '>=' = 'G',
                    '<'  = 'l',
                    '<=' = 'L',
                    '==' = 'n',
                    '!=' = 'N',
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
                  "is.factor" = "is.factor")

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
                   data.frame(type = "character", scalar = TRUE,  row.names = "Key<Model>",   stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = TRUE,  row.names = "int",          stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = FALSE, row.names = "int[]",        stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = TRUE,  row.names = "long",         stringsAsFactors = FALSE),
                   data.frame(type = "numeric",   scalar = FALSE, row.names = "long[]",       stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = TRUE,  row.names = "string",       stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = FALSE, row.names = "string[]",     stringsAsFactors = FALSE),
                   data.frame(type = "character", scalar = TRUE,  row.names = "VecSpecifier", stringsAsFactors = FALSE))

#' Endpoint Version
.h2o.__REST_API_VERSION <- 3L

#' Administrative Endpoints
.h2o.__JOBS           <- "Jobs.json"          # Jobs.json/$90w3r52hfej_JOB_KEY_12389471jsdfs
.h2o.__CLOUD          <- "Cloud.json"
.h2o.__SHUTDOWN       <- "Shutdown.json"
.h2o.__DOWNLOAD_LOGS  <- "/Logs/download"

#' Removal Endpoints
.h2o.__REMOVE         <- "Remove.json"
.h2o.__REMOVEALL      <- "RemoveAll.json"

#' Log and Echo Endpoint
.h2o.__LOGANDECHO     <- "LogAndEcho.json"

#' Import/Export Endpoints
.h2o.__IMPORT         <- "ImportFiles.json"   # ImportFiles.json?path=/path/to/data

#' Parse Endpoints
.h2o.__PARSE_SETUP    <- "ParseSetup.json"    # Sample Usage: ParseSetup?srcs=["nfs://asdfsdf...", "nfs://..."]
.h2o.__PARSE          <- "Parse.json"         # Sample Usage: Parse?srcs=["nfs://path/to/data"]&hex=KEYNAME&pType=CSV&sep=44&ncols=5&checkHeader=0&singleQuotes=false&columnNames=["C1",%20"C2",%20"C3",%20"C4",%20"C5"]

#' Inspect/Summary Endpoints
.h2o.__INSPECT        <- "Inspect.json"       # Inspect.json?key=asdfasdf
.h2o.__FRAMES         <- "Frames.json"        # Frames.json/<key>    example: http://localhost:54321/3/Frames.json/meow.hex
.h2o.__COL_SUMMARY <- function(key, col) paste(.h2o.__FRAMES, key, "columns", col, "summary", sep = "/")

#' Frame Manipulation
.h2o.__CREATE_FRAME   <- "CreateFrame.json"

#' Rapids Endpoint
.h2o.__RAPIDS         <- "Rapids.json"

#' Model Builder Endpoint Generator
.h2o.__MODEL_BUILDERS <- function(algo) paste0("ModelBuilders/", algo, '.json')

#' Model Endpoint
.h2o.__MODELS         <- "Models.json"

#' Word To Vector Endpoints
.h2o.__W2V            <- "Word2Vec.json"
.h2o.__SYNONYMS       <- "Synonyms.json"
.h2o.__TRANSFORM      <- "Transform.json"

#' Model Metrics Endpoint
.h2o.__MODEL_METRICS <- function(model,data) {
  if(missing(data)) {
    paste0("ModelMetrics.json/models/",model)
  }
  else {
    paste0("ModelMetrics.json/models/",model,"/frames/",data)
  }
}
