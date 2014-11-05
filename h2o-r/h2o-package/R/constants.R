#'
#' H2O Package Constants
#'
#' The API endpoints for interacting with H2O via REST are named here.
#'
#' Additionally, environment variables for the H2O package are named here.


#'
#' Inspect Constants
#'
.RESULT_MAX <- 1000
.MAX_INSPECT_ROW_VIEW <- 10000
.MAX_INSPECT_COL_VIEW <- 10000

#'
#' The H2O Package Environment
#'
.pkg.env              <- new.env()

# These may no longer be needed...
.pkg.env$result_count <- 0
.pkg.env$temp_count   <- 0
.pkg.env$IS_LOGGING   <- FALSE
.pkg.env$call_list    <- NULL
.TEMP_KEY <- "Last.value"

#'
#' Some handy utility functions for doing common h2o package-related tasks.
#'
"%<i-%"  <- function(x,y) inherits(x, y)  # check if `x` inherits from `y`
"%i%"    <- function(x,y) inherits(x, y)  # same as above but short hand version
"%instanceof%" <- function(x,y) inherits(x,y) # java friendly version
"%<p0-%" <- function(x,y) assign(deparse(substitute(x)), paste(x, y, sep = ""), parent.frame())  # paste0
"%p0%"   <- function(x,y) assign(deparse(substitute(x)), paste(x, y, sep = ""), parent.frame())  # paste0
"%<p-%"  <- function(x,y) assign(deparse(substitute(x)), paste(x, y), parent.frame()) # paste
"%p%"    <- function(x,y) assign(deparse(substitute(x)), paste(x, y), parent.frame()) # paste
"%<-%"   <- function(x,y) {
  if ( x %i% "H2OParsedData" ) x <- x@key
  new("ASTNode", root= new("ASTApply", op="="), children = list(left = '!' %p0% x, right = y)) # assignment node
}

.uniq.id <- function(prefix = "") {
  hex_digits <- c(as.character(0:9), letters[1:6])
  y_digits <- hex_digits[9:12]
  tempA <- paste(sample(hex_digits, 8, replace=TRUE), collapse='')
  tempB <- paste(sample(hex_digits, 4, replace=TRUE), collapse='')
  tempC <- '4'
  tempD <- paste(sample(hex_digits, 3, replace=TRUE), collapse='')
  tempE <- paste(sample(y_digits,1), collapse='')
  tempF <- paste(sample(hex_digits, 3, replace=TRUE), collapse='')
  tempG <- paste(sample(hex_digits, 12, replace=TRUE), collapse='')
  temp <- tempA %p0% tempB %p0% tempC %p0% tempD %p0% tempE %p0% tempF %p0% tempG
  prefix %p0% '_' %p0% temp
}

#'
#' Map of binary operators to their "AST" operator value.
#'
.op.map <- list('>'  = 'g',
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
                "%/%"="%/%")

.binop.map <- list('>'  = 'g',
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
.unop.map <- list('!'  = '_',
                  '$'  = '[',
                  '['  = '[')

#'
#' A list of prefix operations. 1:1 map for now, may add more to the map as needed.
#'
.prefix.map <- list("abs"  = "abs",
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
                    "sin" = "sin",
                    "sinh" = "sinh",
                    "tan" = "tan",
                    "tanh" = "tanh",
                    "gamma" = "gamma",
                    "lgamma" = "lgamma",
                    "digamma" = "digamma",
                    "trigamma" = "trigamma",
                    "round" = "round",
                    "signif" = "signif",
                    "max" = "max",
                    "min" = "min",
                    "range" = "range",
                    "prod" = "prod",
                    "sum" = "sum",
                    "any" = "any",
                    "all" = "all",
                    "is.na" = "is.na",
                    "trunc" = "trunc")

#'
#' The variable args operations
#'
.varop.map <- list( "round" = "round",
                    "signif" = "signif",
                    "max" = "max",
                    "min" = "min",
                    "range" = "range",
                    "prod" = "prod",
                    "sum" = "sum",
                    "any" = "any",
                    "all" = "all",
                    "log" = "log",
                    "trunc" = "trunc")

#'
#' H2O API endpoints
#'
#'
#' Import/Parse Endpointss
.h2o.__IMPORT       <- "ImportFiles.json"   # ImportFiles.json?path=/path/to/data
.h2o.__PARSE_SETUP  <- "ParseSetup.json"    # ParseSetup?srcs=[nfs://path/to/data]
.h2o.__PARSE        <- "Parse.json"         # Parse?srcs=[nfs://path/to/data]&hex=KEYNAME&pType=CSV&sep=44&ncols=5&checkHeader=0&singleQuotes=false&columnNames=[C1,%20C2,%20C3,%20C4,%20C5]
.h2o.__PARSE_SETUP  <- "ParseSetup.json"    # ParseSetup?srcs=[nfs://asdfsdf..., nfs://...]

#'
#' Inspect/Summary Endpoints
#'
.h2o.__INSPECT      <- "Inspect.json"       # Inspect.json?key=asdfasdf
.h2o.__FRAMES       <- "3/Frames.json"      # Frames.json/<key>    example: http://localhost:54321/3/Frames.json/meow.hex

#'
#' Administrative Endpoints
#'
.h2o.__JOBS         <- "Jobs.json"          # Jobs/$90w3r52hfej_JOB_KEY_12389471jsdfs
.h2o.__CLOUD        <- "Cloud.json"

#'
#' Algorithm Endpoints
#'
.h2o.__KMEANS_PARAMS       <- "2/Kmeans.json"
.h2o.__DEEPLEARNING        <- "2/ModelBuilders/deeplearning.json"
.h2o.__DEEPLEARNING_PARAMS <- "DeepLearning.json"

#'
#' Algorithm Parameter Endpoints
#'

#'
#' Model Predict Endpoint
#'
.h2o.__PREDICT <- "3/Predictions/models/(?<model>.*)/frames/(?<frame>.*)"
#'
#' Cascade/Exec3
#'
.h2o.__CASCADE      <- "Cascade.json"

#'
#' Removal
#'
.h2o.__REMOVE       <- "Remove.json"
.h2o.__REMOVEALL    <- "RemoveAll.json"

#'
#' Log and Echo
#'
.h2o.__LOGANDECHO   <- "LogAndEcho.json"


#'
#' The list of H2O1 (old) endpoints
#######
#.h2o.__PAGE_CANCEL                <- "Cancel.json"
#.h2o.__PAGE_GET                   <- "GetVector.json"
#.h2o.__PAGE_IMPORTURL             <- "ImportUrl.json"
#.h2o.__PAGE_IMPORTFILES           <- "ImportFiles.json"
#.h2o.__PAGE_IMPORTHDFS            <- "ImportHdfs.json"
#.h2o.__PAGE_EXPORTHDFS            <- "ExportHdfs.json"
#.h2o.__PAGE_INSPECT               <- "Inspect.json"
#.h2o.__PAGE_JOBS                  <- "Jobs.json"
#.h2o.__PAGE_PARSE                 <- "Parse.json"
#.h2o.__PAGE_PREDICT               <- "GeneratePredictionsPage.json"
#.h2o.__PAGE_PUT                   <- "PutVector.json"
#.h2o.__PAGE_REMOVE                <- "Remove.json"
#.h2o.__PAGE_REMOVEALL             <- "2/RemoveAll.json"
#.h2o.__PAGE_SUMMARY               <- "SummaryPage.json"
#.h2o.__PAGE_SHUTDOWN              <- "Shutdown.json"
#.h2o.__PAGE_VIEWALL               <- "StoreView.json"
#.h2o.__DOWNLOAD_LOGS              <- "LogDownload.json"
#.h2o.__PAGE_GLM                   <- "GLM.json"
#.h2o.__PAGE_GLMProgress           <- "GLMProgressPage.json"
#.h2o.__PAGE_GLMGrid               <- "GLMGrid.json"
#.h2o.__PAGE_GLMGridProgress       <- "GLMGridProgress.json"
#.h2o.__PAGE_KMEANS                <- "KMeans.json"
#.h2o.__PAGE_KMAPPLY               <- "KMeansApply.json"
#.h2o.__PAGE_KMSCORE               <- "KMeansScore.json"
#.h2o.__PAGE_RF                    <- "RF.json"
#.h2o.__PAGE_RFVIEW                <- "RFView.json"
#.h2o.__PAGE_RFTREEVIEW            <- "RFTreeView.json"
#.h2o.__PAGE_EXEC2                 <- "2/Exec2.json"
#.h2o.__PAGE_IMPORTFILES2          <- "2/ImportFiles2.json"
#.h2o.__PAGE_EXPORTFILES           <- "2/ExportFiles.json"
#.h2o.__PAGE_INSPECT2              <- "2/Inspect2.json"
#.h2o.__PAGE_PARSE2                <- "2/Parse2.json"
#.h2o.__PAGE_PREDICT2              <- "2/Predict.json"
#.h2o.__PAGE_SUMMARY2              <- "2/SummaryPage2.json"
#.h2o.__PAGE_LOG_AND_ECHO          <- "2/LogAndEcho.json"
#.h2o.__HACK_LEVELS                <- "Levels.json"
#.h2o.__HACK_LEVELS2               <- "2/Levels2.json"
#.h2o.__HACK_SETCOLNAMES           <- "SetColumnNames.json"
#.h2o.__HACK_SETCOLNAMES2          <- "2/SetColumnNames2.json"
#.h2o.__PAGE_CONFUSION             <- "2/ConfusionMatrix.json"
#.h2o.__PAGE_AUC                   <- "2/AUC.json"
#.h2o.__PAGE_HITRATIO              <- "2/HitRatio.json"
#.h2o.__PAGE_GAPSTAT               <- "2/GapStatistic.json"
#.h2o.__PAGE_GAPSTATVIEW           <- "2/GapStatisticModelView.json"
#.h2o.__PAGE_QUANTILES             <- "2/QuantilesPage.json"
#.h2o.__PAGE_DRF                   <- "2/DRF.json"
#.h2o.__PAGE_DRFProgress           <- "2/DRFProgressPage.json"
#.h2o.__PAGE_DRFModelView          <- "2/DRFModelView.json"
#.h2o.__PAGE_GBM                   <- "2/GBM.json"
#.h2o.__PAGE_GBMProgress           <- "2/GBMProgressPage.json"
#.h2o.__PAGE_GRIDSEARCH            <- "2/GridSearchProgress.json"
#.h2o.__PAGE_GBMModelView          <- "2/GBMModelView.json"
#.h2o.__PAGE_GLM2                  <- "2/GLM2.json"
#.h2o.__PAGE_GLM2Progress          <- "2/GLMProgress.json"
#.h2o.__PAGE_GLMModelView          <- "2/GLMModelView.json"
#.h2o.__PAGE_GLMValidView          <- "2/GLMValidationView.json"
#.h2o.__PAGE_GLM2GridView          <- "2/GLMGridView.json"
#.h2o.__PAGE_KMEANS2               <- "2/KMeans.json"
#.h2o.__PAGE_KM2Progress           <- "2/KMeans2Progress.json"
#.h2o.__PAGE_KM2ModelView          <- "2/KMeans2ModelView.json"
#.h2o.__PAGE_DeepLearning          <- "2/DeepLearning.json"
#.h2o.__PAGE_DeepLearningProgress  <- "2/DeepLearningProgressPage.json"
#.h2o.__PAGE_DeepLearningModelView <- "2/DeepLearningModelView.json"
#.h2o.__PAGE_PCA                   <- "2/PCA.json"
#.h2o.__PAGE_PCASCORE              <- "2/PCAScore.json"
#.h2o.__PAGE_PCAProgress           <- "2/PCAProgressPage.json"
#.h2o.__PAGE_PCAModelView          <- "2/PCAModelView.json"
#.h2o.__PAGE_SpeeDRF               <- "2/SpeeDRF.json"
#.h2o.__PAGE_SpeeDRFModelView      <- "2/SpeeDRFModelView.json"
#.h2o.__PAGE_BAYES                 <- "2/NaiveBayes.json"
#.h2o.__PAGE_NBProgress            <- "2/NBProgressPage.json"
#.h2o.__PAGE_NBModelView           <- "2/NBModelView.json"
#.h2o.__PAGE_CreateFrame           <- "2/CreateFrame.json"
