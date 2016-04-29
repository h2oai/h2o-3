setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Test naive Bayes on iris_wheader.csv
test.nbayes.iris.cv <- function() {
  Log.info("Importing iris_wheader.csv data...\n")
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"))
  iris.sum <- summary(iris.hex)
  print(iris.sum)

  iris.nbayes.h2o <- h2o.naiveBayes(x = 1:4, y = 5, training_frame = iris.hex[1:99,], validation_frame=iris.hex[100:150,])
  print(iris.nbayes.h2o)
   
  iris.nbayes.h2o <- h2o.naiveBayes(x = 1:4, y = 5, training_frame = iris.hex, nfolds=4, seed=1234)
  print(iris.nbayes.h2o)

  iris.nbayes.h2o <- h2o.naiveBayes(x = 1:4, y = 5, training_frame = iris.hex, nfolds=4, fold_assignment = "Modulo")
  print(iris.nbayes.h2o)

  iris.nbayes.h2o <- h2o.naiveBayes(x = 1:4, y = 5, training_frame = iris.hex, nfolds=4, fold_assignment = "Stratified", seed=1234)
  print(iris.nbayes.h2o)

  set.seed(1234)
  df.hex <- h2o.cbind(iris.hex, as.h2o(sample(1:4,nrow(iris.hex),TRUE)))
  names(df.hex) <- c(names(iris.hex), "myfold")
  print(head(df.hex))
  iris.nbayes.h2o <- h2o.naiveBayes(x = 1:4, y = 5, training_frame = df.hex, fold_column = "myfold")
  print(iris.nbayes.h2o)

  print(iris.nbayes.h2o@model$cross_validation_metrics_summary)
}

doTest("Naive Bayes Test: Iris Data with Cross Validation", test.nbayes.iris.cv)
