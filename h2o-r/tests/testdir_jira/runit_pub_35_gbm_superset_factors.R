setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#
# PUB-35: prediction broken with superset factors
# 1. NAs in their own NA bucket
#
# 2. Train a model where feature F has no NAs.
#    But has an NA in test data.
#    Answer should be NA.
#

# In Dev superset factors shoudl be treated as NA
# Prediction should still go through







pub35gbm <- function(){
  h2oTest.logInfo('uploading gbm training dataset')
  dataset_path = normalizePath(h2oTest.locate('smalldata/jira/pub-35_train.csv'))
  df.h <- h2o.importFile(dataset_path)

  h2oTest.logInfo('printing from h2o')
  h2oTest.logInfo( head(df.h) )

  h2oTest.logInfo("uploading gbm testing dataset")
  dataset_path <- normalizePath(h2oTest.locate('smalldata/jira/pub-35_test.csv'))
  df.h2 <- h2o.importFile(dataset_path)
  h2oTest.logInfo( head(df.h2) )

  h2oTest.logInfo("Training a GBM model")
  m <- h2o.gbm(x = 1:3,
               y = 4,
               training_frame = df.h,
               ntrees = 10,
               max_depth = 5,
               min_rows = 1,
               learn_rate = 0.1,
               distribution = "multinomial")

  preds <- as.data.frame(predict(m, df.h2))

  print(preds)
  print(as.data.frame(df.h2))
  expect_that(is.na(preds[1,1]), equals(FALSE))

  
}

h2oTest.doTest('pub-35-gbm_superset_factors', pub35gbm)
