#'
#' H2O Package Constants.
#'
#' The API endpoints for interacting with H2O via REST are named here. Additionally,
#' environment variables for the H2O package are named here.

.pkg.env              <- new.env()
.pkg.env$result_count <- 0
.pkg.env$temp_count   <- 0
.pkg.env$IS_LOGGING   <- FALSE
.pkg.env$call_list    <- NULL

#"<-" <- function(l, r) {
#    lhs <- substitute(l)
#    e <- .pkg.env
#    assign(as.character(lhs), r, e)
#    lockBinding(lhs, e)
#    invisible(r)
#}

.TEMP_KEY <- "Last.value"
.RESULT_MAX <- 1000
.MAX_INSPECT_ROW_VIEW <- 10000
.MAX_INSPECT_COL_VIEW <- 10000
.LOGICAL_OPERATORS <- c("==", ">", "<", "!=", ">=", "<=", "&", "|", "&&", "||", "!", "is.na")
.INFIX_OPERATORS   <- c("+", "-", "*", "/", "^", "%%", "%/%", "&", "|", "!", "==", "!=", "<", "<=", ">=", ">")

.op.map <- list('>' = 'g', '>=' = 'G', '<' = 'l', '<=' = 'L', '==' = 'N', '!=' = 'n', '%%' = '%', '**' = '^',
                '|'='|', '&'='&', "&&"="&&", '+'='+', '-'='-', '*'='*', '/'='/', '^'='^', "%/%"="%/%")

#'
#' H2O API endpoints
#'

#'
#' Import & Parse Endpointss
.h2o.__IMPORT       <- "ImportFiles.json"   # ImportFiles.json?path=/path/to/data
.h2o.__PARSE_SETUP  <- "ParseSetup.json"    # ParseSetup?srcs=[nfs://path/to/data]
.h2o.__PARSE        <- "Parse.json"         # Parse?srcs=[nfs://path/to/data]&hex=KEYNAME&pType=CSV&sep=44&ncols=5&checkHeader=0&singleQuotes=false&columnNames=[C1,%20C2,%20C3,%20C4,%20C5]
.h2o.__PARSE_SETUP  <- "ParseSetup.json"    # ParseSetup?srcs=[nfs://asdfsdf..., nfs://...]

#'
#' Summary and Inspect Endpoints
.h2o.__INSPECT      <- "Inspect.json"       # Inspect.json?key=asdfasdf
.h2o.__FRAMES       <- "3/Frames.json"      # Frames.json/<key>    example: http://localhost:54321/3/Frames.json/meow.hex

#'
#' Administrative Endpoints
.h2o.__JOBS         <- "Jobs.json"          # Jobs/$90w3r52hfej_JOB_KEY_12389471jsdfs
.h2o.__CLOUD        <- "Cloud.json"

#'
#' Algorithmic Endpoints
.h2o.__KMEANS       <- "v2/Kmeans.json"

#'
#' Cascade
.h2o.__CASCADE      <- "Cascade.json"

#'
#' Removal
.h2o.__REMOVE       <- "Remove.json"

#.h2o.__PAGE_EXEC3                 <- "2/Exec3.json"
#.h2o.__PAGE_CANCEL                <- "Cancel.json"
#.h2o.__PAGE_CLOUD                 <- "Cloud.json"
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
