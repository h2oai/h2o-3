setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.tempdir <- function() {
  
  data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
  cols <- c("Origin", "Distance")
  model <- h2o.gbm(x=cols, y = "IsDepDelayed", training_frame = data, validation_frame = data, nfolds = 3, ntrees = 1)
  mojo_name = h2o.download_mojo(model = model,path = tempdir(), get_genmodel_jar = TRUE
                                , genmodel_name = "genmodel.jar", genmodel_path = tempdir())
  frame = as.data.frame(data)
  pred <- h2o.mojo_predict_df(frame = frame, mojo_zip_path = paste0(tempdir(),"/", mojo_name), genmodel_jar_path = paste0(tempdir(), "/", "genmodel.jar"))
  
  expect_true(file.exists(tempdir()))
  expect_true(file.exists(paste0(tempdir(),"/","genmodel.jar")))
  expect_true(file.exists(paste0(tempdir(), "/", mojo_name)))
  
  unlink(paste0(tempdir(),"/","genmodel.jar"))
  unlink(paste0(tempdir(), "/", mojo_name))
  
  
  

}

doTest("Tempdir remains undeleted/unaffected after mojo_predict_df is called", test.tempdir)
