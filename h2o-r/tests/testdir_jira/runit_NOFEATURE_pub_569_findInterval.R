#
# Exec2 pop assert
#




the_test <- function(){
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

  
}

doTest('the_test', the_test)

