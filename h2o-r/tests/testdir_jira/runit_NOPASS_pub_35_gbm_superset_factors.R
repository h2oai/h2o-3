#
# PUB-35: prediction broken with superset factors
# 1. NAs in their own NA bucket
#
# 2. Train a model where feature F has no NAs.
#    But has an NA in test data. 
#    Answer should be NA.
#


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))

source('../findNSourceUtils.R')


pub35gbm <- function(conn){
  Log.info('uploading gbm training dataset')
  dataset_path = normalizePath(locate('smalldata/jira/pub-35_train.csv'))
  df.h <- h2o.importFile(conn, dataset_path)

  Log.info('printing from h2o')
  Log.info( head(df.h) )

  Log.info("uploading gbm testing dataset")
  dataset_path <- normalizePath(locate('smalldata/jira/pub-35_test.csv'))
  df.h2 <- h2o.importFile(conn, dataset_path)
  Log.info( head(df.h2) )

  Log.info("Training a GBM model")
  m <- h2o.gbm(x = 1:3, 
               y = 4, 
               data = df.h,
               n.trees = 10,
               interaction.depth = 5,  
               n.minobsinnode = 10, 
               shrinkage = 0.1)

  preds <- as.data.frame(h2o.predict(m, df.h2))

  print(preds)
  expect_that(is.na(preds[1,1]), equals(TRUE))

  testEnd()
}

doTest('pub-35-gbm_superset_factors', pub35gbm)
