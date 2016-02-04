setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Generate lots of keys then remove them
##




test <- function() {

  hex <- as.h2o(iris)

  Log.info("Original factor column")
  print(hex$Species)
  print(h2o.levels(hex$Species))

  zz <- h2o.setLevels(hex$Species, c(setosa = "NEW SETOSA ENUM", virginica = "NEW VIRG ENUM", versicolor = "NEW VERSI ENUM"))

  Log.info("Revalued factor column")
  print(zz)
  print(h2o.levels(zz))
  vals <- c("NEW SETOSA ENUM", "NEW VIRG ENUM", "NEW VERSI ENUM")
  expect_equal(h2o.levels(zz), vals)

  
}

doTest("Many Keys Test: Removing", test)

