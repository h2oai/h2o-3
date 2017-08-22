setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
# Test PCA on car.arff.txt
test.pca.slow <- function() {
  data = h2o.uploadFile(locate("bigdata/laptop/jira/re0.wc.arff.txt.zip"),destination_frame = "data",header = T)
  data = data[,-2887]
  data2 = as.data.frame(data)

  print("Running R PCA...")
  ptm <- proc.time()
  fitR <- prcomp(data2, center = T, scale. = T)
  timepassed = proc.time() - ptm
  print(timepassed)

  print("Running H2O PCA with GramSVD...")
  ptm <- proc.time()
  mm = h2o.prcomp(data,transform = "STANDARDIZE",k =1504, pca_method="GramSVD")
  h2otimepassed = proc.time() - ptm
  print(h2otimepassed)
  h2o.rm(mm)
  ptm <- proc.time()

  print("Running with GLRM...")
  mmGLRM = h2o.glrm(data, transform="STANDARDIZE", k = 1504, init="Random", max_iterations=3, recover_svd=TRUE, regularization_x="None", regularization_y="None")
  h2otimepassed = proc.time() - ptm
  print(h2otimepassed)
  h2o.rm(mmGLRM)

  print("Running H2O PCA with Randomized...")
  ptm <- proc.time()
  mm = h2o.prcomp(data,transform = "STANDARDIZE",k =1504, max_iterations=3, pca_method="Randomized")
  h2otimepassed = proc.time() - ptm
  print(h2otimepassed)
  h2o.rm(mm)
  h2o.rm(data)
}
doTest("PCA Test: rec0.wc.arff.txt", test.pca.slow)