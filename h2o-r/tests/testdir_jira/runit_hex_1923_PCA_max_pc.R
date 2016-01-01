setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.PCA.australia <- function() {
  h2oTest.logInfo("Importing AustraliaCoast.csv data...\n")
  australia.data = read.csv(h2oTest.locate("smalldata/pca_test/AustraliaCoast.csv"), header = TRUE)
  australia.hex = h2o.importFile(h2oTest.locate( "smalldata/pca_test/AustraliaCoast.csv",))
  australia.sum = summary(australia.hex)
  print(australia.sum)
  
  h2oTest.logInfo("H2O PCA on Australia coastline data:\n")
  australia.pca = h2o.prcomp(australia.hex, k = 8, transform = "STANDARDIZE")

  h2oTest.logInfo("H2O PCA on Australia coastline data returning only first 2 components:\n")
  australia.pca2 = h2o.prcomp(australia.hex, k = 2, transform = "STANDARDIZE")
  
  expect_equal(ncol(australia.pca@model$eigenvectors), 8)
  expect_equal(ncol(australia.pca2@model$eigenvectors), 2)
  
}

h2oTest.doTest("PCA: Australia Data", test.PCA.australia)

