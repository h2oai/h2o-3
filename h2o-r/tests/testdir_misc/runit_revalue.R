setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Set levels of a factor column
##




test.setLevels <- function() {

  hex <- as.h2o(iris)
  hex.species.copy <- hex$Species
  species.orig <- h2o.levels(hex$Species)

  Log.info("Tests in-place modification")
  hex$Species <- h2o.setLevels(hex$Species, c(setosa = "NEW SETOSA ENUM", virginica = "NEW VIRG ENUM", versicolor = "NEW VERSI ENUM"))
  vals <- c("NEW SETOSA ENUM", "NEW VIRG ENUM", "NEW VERSI ENUM")
  expect_equal(h2o.levels(hex$Species), vals)
  expect_equal(h2o.levels(hex.species.copy), vals) # setLevels has side effects

  Log.info("Tests copy-on-write modification")
  hex$Species <- h2o.setLevels(hex$Species, species.orig, in.place = FALSE)
  expect_equal(h2o.levels(hex$Species), species.orig)
                                                                                                                                   }

doTest("Set levels of a factor column", test.setLevels)