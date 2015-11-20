


test.PCA.australia <- function() {
  Log.info("Importing AustraliaCoast.csv data...\n")
  australia.data = read.csv(locate("smalldata/pca_test/AustraliaCoast.csv"), header = TRUE)
  australia.hex = h2o.importFile(locate( "smalldata/pca_test/AustraliaCoast.csv",))
  australia.sum = summary(australia.hex)
  print(australia.sum)
  
  Log.info("H2O PCA on Australia coastline data:\n")
  australia.pca = h2o.prcomp(australia.hex, k = 8, transform = "STANDARDIZE")

  Log.info("H2O PCA on Australia coastline data returning only first 2 components:\n")
  australia.pca2 = h2o.prcomp(australia.hex, k = 2, transform = "STANDARDIZE")
  
  expect_equal(ncol(australia.pca@model$eigenvectors), 8)
  expect_equal(ncol(australia.pca2@model$eigenvectors), 2)
  
}

doTest("PCA: Australia Data", test.PCA.australia)

