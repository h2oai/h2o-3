setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
test.gbm.javapredict.smtrees <- function() {
n.trees <- 3
interaction.depth <- 1
n.minobsinnode <- 2
shrinkage <- 1
distribution <- "gaussian"
train <- locate("smalldata/gbm_test/smtrees.csv")
training_frame <- h2o.importFile(train)
test <- locate("smalldata/gbm_test/smtrees.csv")
test_frame <- h2o.importFile(test)
x = c("girth","height")
y = "vol"

params <- list()
params$ntrees <- n.trees
params$max_depth <- interaction.depth
params$min_rows <- n.minobsinnode
params$x <- x
params$y <- y
params$training_frame <- training_frame

doJavapredictTest("gbm",test,test_frame,params)

}
#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
doTest("GBM test", test.gbm.javapredict.smtrees)
