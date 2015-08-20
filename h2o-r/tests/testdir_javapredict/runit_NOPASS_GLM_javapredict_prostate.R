#----------------------------------------------------------------------
# Purpose:  This test exercises the GLM model downloaded as java code.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
# setwd("/Users/tomk/0xdata/ws/h2o-3/h2o-r/tests/testdir_javapredict")
TEST_ROOT_DIR <- ".."
source("../h2o-runit.R")
source("../Utils/shared_javapredict.R")
options(echo = TRUE)

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

training_file          <- h2o:::.h2o.locate("smalldata/prostate/prostate.csv")
training_frame         <- h2o.importFile(training_file)
training_frame$CAPSULE <- as.factor(training_frame$CAPSULE)

test_file              <- h2o:::.h2o.locate("smalldata/prostate/prostate.csv")
test_frame             <- h2o.importFile(test_file)
test_frame$CAPSULE     <- as.factor(test_frame$CAPSULE)

y <- "CAPSULE"
x <- c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")

#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
doJavapredictTest(model = "glm", training_frame = training_frame, test_file = test_file, test_frame = test_frame,
                  y = y,
                  x = x,
                  family = "binomial")

PASS()
