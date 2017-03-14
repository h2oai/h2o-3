setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
test.GLM.nonnegative <- function() {
  Log.info("Importing prostate.csv data...\n")
  df = h2o.importFile(locate("smalldata/prostate/prostate_cat.csv"), "prostate.hex")
  x = names(df)[-which(names(df) %in% c("ID","CAPSULE"))]
  m = h2o.glm(training_frame = df,x=x,y="CAPSULE",family='binomial',lambda=0,compute_p_values = TRUE,missing_values_handling = "Skip")
  p = h2o.predict(m,df)
  w = p[,3]*p[,2]
  names(w) <- c("w")
  X = h2o.cbind(df[x],w)
  gfr = h2o.computeGram(X=X,weights="w",skip_missing=TRUE,standardize=FALSE)
  gm = as.matrix(gfr)
  rownames(gm) <- colnames(gm)
  h2o.rm(h2o.getId(gfr))
  ginvDiag = diag(solve(gm))
  ginvDiag = ginvDiag[c(length(ginvDiag),1:length(ginvDiag)-1)]
  beta = m@model$coefficients_table[,2]
  names(beta) <- m@model$coefficients_table[,1]
  zvalues = beta/sqrt(ginvDiag)
  zvalues_h2o = m@model$coefficients_table[,4]
  print(as.data.frame(m@model$coefficients_table))
  print(cbind(zvalues,zvalues_h2o))
  if(max(abs(zvalues-zvalues_h2o)) > 1e-4) fail("z-scores do not match")
  #test 2 compare non-weighted non-standardized matrix to t(M) %*% M
  dfr = as.data.frame(df)
  X2 = df[c("CAPSULE","DPROS","RACE","DCAPS","AGE","PSA","VOL","GLEASON")]
  X2r = as.data.frame(X2)
  X2 = X2[,2:8]
  M = model.matrix(data = X2r,CAPSULE~.)
  G = t(M) %*% M
  gfr2 = h2o.computeGram(X=X2,skip_missing=TRUE,standardize=FALSE)
  gm2 = as.matrix(gfr2)
  rownames(gm2) <- colnames(gm2)
  h2o.rm(h2o.getId(gfr2))
  if(max(abs(G[c(2:11,1),c(2:11,1)] - gm2)) > 1e-8)fail("grams do not match")
}
doTest("GLM Test: Prostate", test.GLM.nonnegative)