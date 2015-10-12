setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_naive_bayes.golden <- function() {
  
  votesPath <- locate("smalldata/extdata/housevotes.csv", package="h2o")
  votes.hex <- h2o.uploadFile(path = votesPath, header = TRUE)
  h2o.naiveBayes(x = 2:17, y = 1, training_frame = votes.hex, laplace = 3)
  
  
}

doTest("R Doc Naive Bayes", test.rdoc_naive_bayes.golden)
