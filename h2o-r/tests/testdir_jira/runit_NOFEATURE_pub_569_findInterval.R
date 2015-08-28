#
# Exec2 pop assert
#

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

the_test <- function(conn){
  df = h2o.importFile(locate('smalldata/jira/pub-569.csv'))

  metric.quantilesScore <- function(valuesArray) {
    numberOfLevels <- 100
    quantileLevels <- quantile(valuesArray, probs = seq(0, 1, by = 1/numberOfLevels))
    scores <- seq(1, numberOfLevels, 1)
    rightmost.closed = T
    interval <- findInterval(valuesArray, quantileLevels, rightmost.closed)
    scores[interval, 1]
  }
  
  scores <- apply(df, 2, metric.quantilesScore)

  testEnd()
}

doTest('the_test', the_test)

