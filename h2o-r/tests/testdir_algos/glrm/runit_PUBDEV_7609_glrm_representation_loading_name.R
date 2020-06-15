setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glrm.arrests <- function() {
  Log.info("Importing arrests.csv data...") 
  arrestsR <- read.csv(locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  initCent <- scale(arrestsR, center = TRUE, scale = FALSE)[1:4,]
  loading_name <- "are_you_confused"
  representation_name <- "are_we_confused"
  #use representation_name
  fitH2O <- h2o.glrm(arrestsH2O, k = 4, init = "User", user_y = initCent, transform = "DEMEAN", 
                     loss = "Quadratic", regularization_x = "None", regularization_y = "None", 
                     recover_svd = TRUE, representation_name=representation_name)
  print("fitH2O@model$representation_name is ")
  print(fitH2O@model$representation_name)
  print("representation name is ")
  print(representation_name)
  expect_true(fitH2O@model$representation_name==representation_name)
  
  #use loading_name
  expect_warning(fitH2O<-h2o.glrm(fitH2O<-arrestsH2O, k = 4, init = "User", user_y = initCent, transform = "DEMEAN", 
                     loss = "Quadratic", regularization_x = "None", regularization_y = "None", 
                     recover_svd = TRUE, loading_name=loading_name))
  print("fitH2O@model$representation_name is ")
  print(fitH2O@model$representation_name)
  print("loading_name is ")
  print(loading_name)
  expect_true(fitH2O@model$representation_name==loading_name)
  
  #use both representation_name and loading_name
  expect_warning(fitH2O<-h2o.glrm(fitH2O<-arrestsH2O, k = 4, init = "User", user_y = initCent, transform = "DEMEAN", 
                                  loss = "Quadratic", regularization_x = "None", regularization_y = "None", 
                                  recover_svd = TRUE, loading_name=loading_name, representation_name=representation_name))
  print("fitH2O@model$representation_name is ")
  print(fitH2O@model$representation_name)
  print("representation name is ")
  print(representation_name)
  expect_true(fitH2O@model$representation_name==representation_name)
}

doTest("GLRM test representation name, loading_name", test.glrm.arrests)
