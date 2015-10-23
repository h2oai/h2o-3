


# Test naive Bayes on iris_wheader.csv
test.nbayes.iris <- function() {
  Log.info("Importing iris_wheader.csv data...\n")
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"))
  iris.sum <- summary(iris.hex)
  print(iris.sum)
   
  laplace_range <- seq(0, 1, 0.25)
  for(i in laplace_range) {
    Log.info(paste("H2O Naive Bayes with Laplace smoothing = ", i, ":\n", sep = ""))
    iris.nbayes.h2o <- h2o.naiveBayes(x = 1:4, y = 5, training_frame = iris.hex, laplace = as.numeric(i))
    print(iris.nbayes.h2o)
  }
  
  
}

doTest("Naive Bayes Test: Iris Data with Laplace Smoothing", test.nbayes.iris)
