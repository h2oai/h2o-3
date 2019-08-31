setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.tempdir <- function() {
  
  data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
  cols <- c("Origin", "Distance")
  model <- h2o.gbm(x=cols, y = "IsDepDelayed", training_frame = data, validation_frame = data, nfolds = 3, ntrees = 1)
  
  tmpfolder <- paste0(tempdir(),'/', stringi::stri_rand_strings(1,20), '/')
  dir.create(tmpfolder)
  mojo_name = h2o.download_mojo(model = model,path = tmpfolder, get_genmodel_jar = TRUE
                                , genmodel_name = "genmodel.jar", genmodel_path = tmpfolder)
  
  frame = as.data.frame(data)
  pred <- h2o.mojo_predict_df(frame = frame, mojo_zip_path = paste0(tmpfolder, mojo_name), genmodel_jar_path = paste0(tmpfolder, "genmodel.jar"))
  
  expect_true(file.exists(tmpfolder))
  expect_true(file.exists(paste0(tmpfolder,"genmodel.jar")))
  expect_true(file.exists(paste0(tmpfolder, mojo_name)))
  
  unlink(x = tmpfolder, recursive = TRUE)

}

doTest("Tempdir remains undeleted/unaffected after mojo_predict_df is called", test.tempdir)
