setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


pub35gbm <- function(){
  Log.info('uploading gbm training dataset')
  dataset_path = normalizePath(locate('smalldata/jira/pub-35_train.csv'))
  df.h <- h2o.importFile( dataset_path)

  Log.info('printing from h2o')
  Log.info( head(df.h) )

  Log.info("uploading gbm testing dataset")
  dataset_path <- normalizePath(locate('smalldata/jira/pub-35_test.csv'))
  df.h2 <- h2o.importFile( dataset_path)
  Log.info( head(df.h2) )

  Log.info("Training a GBM model")
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

doTest('pub-35-gbm_superset_factors', pub35gbm)
