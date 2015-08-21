##
# Generate lots of keys then remove them
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test <- function(conn) {
  Log.info("Upload iris dataset into H2O...")
  iris.hex = as.h2o(iris)
  
  Log.info("Find the factor levels h2o and R frame...")
  levels1 = sort(h2o.levels(iris.hex$Species))
  levels2 = sort(levels(iris$Species))
  print("Factor levels for Species column for H2OFrame...")
  print(levels1)
  print("Factor levels for Species column for dataframe...")
  print(levels2)
  if(all(levels1 == levels2)){
    Log.info("Factor levels matches for Species Column...")
  } else {
    stop("Factor levels do not match for Species Column...")
  }
  
  Log.info("Try printing the levels of a numeric column...")
  levels1 = levels(iris$Sepal.Length)
  levels2 = h2o.levels(iris.hex$Sepal.Length)
  print("Factor levels for Sepal.Length column for H2OFrame...")
  print(levels1)
  print("Factor levels for Sepal.Length column for dataframe...")
  print(levels2)  
  if(!is.null(levels1)) stop("Numeric Column should not have any factor levels...")

  testEnd()
}

doTest("Print factor levels with h2o.levels:", test)

