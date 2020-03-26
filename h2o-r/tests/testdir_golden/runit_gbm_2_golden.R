setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.gbmMAEgauss.golden <- function() {
  smtreesH2O <- h2o.importFile(locate("smalldata/gbm_test/smtrees.csv"), destination_frame="smtreesH2O")
  smtreesH2O$segment <- as.factor(smtreesH2O$C1 < 6) 
  #fith2o <- h2o.gbm(x=c("girth", "height"), y="vol", ntrees=3, max_depth=1, distribution="gaussian", min_rows=2, learn_rate=.1, training_frame=smtreesH2O)
  segment_models <- h2o.bulk_gbm(x=c("girth", "height"), y="vol", ntrees=3, max_depth=1, seed=42, 
                                 distribution="gaussian", min_rows=2, training_frame=smtreesH2O, segment_columns="segment")
  segment_models_df <- as.data.frame(segment_models)
  print(segment_models_df)
}

doTest("GBM Test: Golden GBM - MAE for GBM Regression", test.gbmMAEgauss.golden)
