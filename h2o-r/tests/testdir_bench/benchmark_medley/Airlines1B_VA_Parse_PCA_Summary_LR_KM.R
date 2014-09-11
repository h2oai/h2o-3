options(echo = F)
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')


pcaModelTime_12col <<- 0
pcaModelTime_9col <<- 0
summaryTime <<- 0
glmModelTime_12col <<- 0
glmModelTime_9col <<- 0
kmModelTime_7col <<- 0
parseTime <<- 0

scoreTime <<- 0
numTrainRows <<- 0
numCols <<- 0
numTestRows <<- 0 

reportScoring <- 0

epilogue<-
function(reportScoring) {
#  p <- sprintf("Parsing the airlines2k dataset: [%d rows] [%d cols] took %f seconds.", 
#                numTrainRows, numCols, parseTime)
#
#  m <- sprintf("Modeling the airlines2k dataset [%d rows] [%d cols] took %f seconds.",
#                numTrainRows, numCols, modelTime)
#
#  s <- sprintf("Scoring the model with the airlines2k dataset [%d rows] [%d cols] took %f seconds.",
#                numTestRows, numCols, scoreTime)


  cat("\n\n\n\n")

  #cat(p, "\n")
  #cat(m, "\n")
  #if (reportScoring) cat(s, "\n")

  cat("\n")
  cat("#########################################")
  cat("\n# Timing Results:                       #")
  cat("\n#                                       #\n")
  cat("#                                       #\n")
  cat("#  ", sprintf("parse     time:       %5.2f s", parseTime), "      #\n")
  cat("#  ", sprintf("summary   time:       %5.2f s", summaryTime), "      #\n")
  cat("#  ", sprintf("pca12col  time:       %5.2f s", pcaModelTime_12col), "      #\n")
  cat("#  ", sprintf("pca9col   time:       %5.2f s", pcaModelTime_9col), "      #\n")
  cat("#  ", sprintf("glm12col  time:       %5.2f s", glmModelTime_12col), "      #\n")
  cat("#  ", sprintf("glm9col   time:       %5.2f s", glmModelTime_9col), "      #\n")
  cat("#  ", sprintf("kmean7col time:       %5.2f s", kmModelTime_7col), "      #\n")
  if (reportScoring) {
  cat("#  ", sprintf("score time:       %5.2f s", scoreTime), "          #\n")
  }
#  cat("#  ",sprintf("Total time taken: %5.2f s", (parseTime + modelTime + scoreTime)), "          #\n")
  cat("#                                       #\n")
  cat("#                                       #\n")
  cat("#########################################\n\n")
} 

medley.bench<-
function(conn) {
  if (conn@ip == "127.0.0.1") {
    start_parse <- round(System$currentTimeMillis())[[1]]
    print("DOING PARSE TRAINING DATA")
    hex <- h2o.uploadFile(conn, locate("smalldata/airlines/allyears2k_headers.zip"), key="air.hex")
    end_parse <- round(System$currentTimeMillis())[[1]]
    parseTime <<- (end_parse - start_parse)/1000
  } else {
    start_parse <- round(System$currentTimeMillis())[[1]]
    print("DOING PARSE OF TRAINING DATA")
    h2o.importFile(h, "s3n://h2o-bench/AirlinesClean2", key = "air.hex", parse = TRUE)
    end_parse <- round(System$currentTimeMillis())[[1]]
    parseTime <<- (end_parse - start_parse)/1000
  }

  numRows <<- nrow(hex)
  numCols <<- ncol(hex)
 
  print("DOING PCA - 12COLUMNS")
  start_model <- round(System$currentTimeMillis())[[1]]
  m <- h2o.prcomp(data = hex)
  end_model <- round(System$currentTimeMillis())[[1]]
  pcaModelTime_12col <<- (end_model - start_model)/1000

  print("DOING PCA - 9COLUMNS")
  start_model <- round(System$currentTimeMillis())[[1]]
  m <- h2o.prcomp(data = hex, ignored_cols = "UniqueCarrier,Origin,Dest")
  end_model <- round(System$currentTimeMillis())[[1]]
  pcaModelTime_9col <<- (end_model - start_model)/1000

  print("DOING SUMMARY")
  start_model <- round(System$currentTimeMillis())[[1]]
  summary(hex)
  end_model <- round(System$currentTimeMillis())[[1]]
  summaryTime <<- (end_model - start_model)/1000

  print("DOING GLM-LR - 12 COLUMNS")
  start_model <- round(System$currentTimeMillis())[[1]]
  m <- h2o.glm(x = c("Year", "Month", "DayofMonth", "DayOfWeek", "CRSDepTime","CRSArrTime", "UniqueCarrier", "CRSElapsedTime", "Origin", "Dest", "Distance"), y = "IsDepDelayed", data = hex, family = "binomial")
  end_model <- round(System$currentTimeMillis())[[1]]
  glmModelTime_12col <<- (end_model - start_model)/1000
 
  print("DOING GLM-LR - 9 COLUMNS")
  start_model <- round(System$currentTimeMillis())[[1]]
  m <- h2o.glm(x = c("Year", "Month", "DayofMonth", "DayOfWeek", "CRSDepTime","CRSArrTime", "CRSElapsedTime", "Distance"), y = "IsDepDelayed", data = hex, family = "binomial")
  end_model <- round(System$currentTimeMillis())[[1]]
  glmModelTime_9col <<- (end_model - start_model)/1000
 
  print("DOING KMEANS")
  start_model <- round(System$currentTimeMillis())[[1]]
  m <- h2o.kmeans(hex, centers = 6, cols = c("Year", "Month", "DayofMonth", "DayOfWeek", "CRSDepTime","CRSArrTime", "Distance"), iter.max = 100) 
  end_model <- round(System$currentTimeMillis())[[1]]
  kmModelTime_7col <<- (end_model - start_model)/1000
  
  epilogue(FALSE)
  testEnd()
}

doTest("Medley of Algos Benchmark Test", medley.bench)

