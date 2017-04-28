setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.pca.stdev <- function() {
  octSvalNonS = c(8.11749143866708e+03, 1.91198996371557e+01, 6.84969678692483e+00, 4.23027849044167e+00,
  2.75766744138058e+00, 1.42933668783258e+00, 5.70343177872293e-01, 4.15357016118369e-01, 2.95791605907287e-01,
  2.20870426059451e-01, 1.47352158893313e-01, 1.28639720294065e-01, 4.44597204285025e-02, 2.41348052897366e-04)
  ranks = 14
  x = read.csv(locate("smalldata/pca_test/decathlon.csv"))
  dd = model.matrix(~Competition-1,data = x)
  df = data.frame(x[,-13],dd)
  hf = as.h2o(x = df,destination_frame = "df")
  fitR <- prcomp(df)
  fitH2O <- h2o.prcomp(hf, k = ranks, max_iterations = 10000)
  t = fitH2O@model$importance
  h2oSvals = c(t$pc1[1], t$pc2[1], t$pc3[1], t$pc4[1], t$pc5[1], t$pc6[1], t$pc7[1], t$pc8[1], t$pc9[1], t$pc10[1],
  t$pc11[1], t$pc12[1], t$pc13[1], t$pc14[1])
  rSvals = fitR$sdev
  expect_equal(h2oSvals, octSvalNonS, tolerance=1e-6) # H2O agrees with Octave
  maxDiff = max(abs(rSvals-octSvalNonS))
  if (maxDiff > 1e-6) {
    print("R PCA does not work when datasets vary by orders of magnitude with no standardization.")
    print("Maximum difference is ")
    print(maxDiff)
  } else {
    print("R has fixed its PCA when datasets vary by orders of magnitude with no standardization.")
  }

  # compare performance of between R and H2O PCA
  oSvalS = c(2.19298154886088e+00, 1.62058654712055e+00, 1.31723014627932e+00, 1.16714881677782e+00,
  1.05740567001831e+00, 7.91111515881530e-01, 7.73880581376863e-01, 6.44061603031255e-01, 5.56001018263363e-01,
  4.49910456827073e-01, 3.90358106689992e-01, 2.13399504842436e-01, 7.31539460616808e-03, 1.89979475731462e-16)
  fitH2Os <- h2o.prcomp(hf, k = ranks, max_iterations = 10000,transform = "STANDARDIZE")
  fitRs <- prcomp(df,center = T,scale. = T)   # use some sort of randomized method.  Our GramSVD is exact.
  isFlipped1 <- checkPCAModel(fitH2Os, fitRs, tolerance=2, compare_all_importance=FALSE)

  # compare R and H2O singular values with Octave output
  t = fitH2Os@model$importance
  h2oSvals = c(t$pc1[1], t$pc2[1], t$pc3[1], t$pc4[1], t$pc5[1], t$pc6[1], t$pc7[1], t$pc8[1], t$pc9[1], t$pc10[1],
  t$pc11[1], t$pc12[1], t$pc13[1], t$pc14[1])
  rSvals = fitRs$sdev
  expect_equal(oSvalS, h2oSvals, tolernace=1e-6)
  expect_equal(oSvalS, rSvals, tolerance=1e-6)
}

doTest("PCA Test: prcmp data", test.pca.stdev)
