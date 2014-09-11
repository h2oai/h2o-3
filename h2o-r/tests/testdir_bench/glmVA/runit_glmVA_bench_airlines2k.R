options(echo = F)
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

parseTime <<- 0
modelTime <<- 0
scoreTime <<- 0
numTrainRows <<- 0
numCols <<- 0
numTestRows <<- 0 

reportScoring <- 0

epilogue<-
function() {
  p <- sprintf("Parsing the airlines2k dataset: [%d rows] [%d cols] took %f seconds.", 
                numTrainRows, numCols, parseTime)

  m <- sprintf("Modeling the airlines2k dataset [%d rows] [%d cols] took %f seconds.",
                numTrainRows, numCols, modelTime)

  s <- sprintf("Scoring the model with the airlines2k dataset [%d rows] [%d cols] took %f seconds.",
                numTestRows, numCols, scoreTime)


  cat("\n\n\n\n")

  cat(p, "\n")
  cat(m, "\n")
  if (reportScoring) cat(s, "\n")

  cat("\n")
  cat("#########################################")
  cat("\n# Timing Results:                       #")
  cat("\n#                                       #\n")
  cat("#                                       #\n")
  cat("#  ", sprintf("parse time:       %5.2f s", parseTime), "          #\n")
  cat("#  ", sprintf("modeling time:    %5.2f s", modelTime), "          #\n")
  if (reportScoring) {
  cat("#  ", sprintf("score time:       %5.2f s", scoreTime), "          #\n")
  }
  cat("#  ",sprintf("Total time taken: %5.2f s", (parseTime + modelTime + scoreTime)), "          #\n")
  cat("#                                       #\n")
  cat("#                                       #\n")
  cat("#########################################\n\n")
} 

glm.airlines.bench<-
function(conn) {
  start_parse <- round(System$currentTimeMillis())[[1]]
  #PARSE TRAINING DATA
  air.hex <- h2o.uploadFile(conn, locate("smalldata/airlines/allyears2k_headers.zip"), key="air.hex")
  end_parse <- round(System$currentTimeMillis())[[1]]
  parseTime <<- (end_parse - start_parse)/1000

  numRows <- nrow(air.hex)
  numCols <- ncol(air.hex)
  
  start_model <- round(System$currentTimeMillis())[[1]]
  m <- h2o.glm(x = c("Year", "Month", "DayofMonth", "DayOfWeek", "CRSDepTime","CRSArrTime", "UniqueCarrier", "CRSElapsedTime", "Origin", "Dest", "Distance"), y = "IsDepDelayed", data = air.hex, family = "binomial")
  end_model <- round(System$currentTimeMillis())[[1]]
  modelTime <<- (end_model - start_model)/1000
  
  #PARSE TESTING DATA
  #

  start_score <- round(System$currentTimeMillis())[[1]]
  #SCORE TESTING DATA WITH MODEL
  end_score <- round(System$currentTimeMillis())[[1]]
  
  epilogue()
  testEnd()
}

doTest("Airlines GLM VA Benchmark Test", glm.airlines.bench)

