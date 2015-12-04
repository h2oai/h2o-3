setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
test.gbm.javapredict.prostrate <- function() {
n.trees <- 100
interaction.depth <- 5
n.minobsinnode <- 10
shrinkage <- 0.1
distribution <- "gaussian"
train <- locate("smalldata/logreg/prostate.csv")
training_frame <- h2o.importFile(train)
test <- locate("smalldata/logreg/prostate.csv")
test_frame <- h2o.importFile(test)

x = c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
y = "CAPSULE"

params <- list()
params$ntrees <- n.trees
params$max_depth <- interaction.depth
params$min_rows <- n.minobsinnode
params$learn_rate <- shrinkage
params$x <- x
params$y <- y
params$training_frame <- training_frame

doJavapredictTest("gbm",test,test_frame,params)
}

#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
#source('../Utils/shared_javapredict_GBM.R') # bad test format, do not know which directory test is going to be called.
doTest("GBM test", test.gbm.javapredict.prostrate)
