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

raw_file <- h2o:::.h2o.locate("smalldata/airlines/allyears2k_headers.zip")
air <- h2o.importFile(raw_file)
s <- h2o.runif(air, seed = 1234)
training_frame = air[s <= 0.8,]
test_frame = air[s > 0.8,]

test_file = paste(tempdir(), "airtest.csv", sep='/')
print("")
print(paste("WRITING TEST FILE:", test_file))
print("")
h2o.exportFile(test_frame, path = test_file, force = TRUE) 

y <- "IsDepDelayed"
x <- c("Year","Month","DayofMonth","DayOfWeek","CRSDepTime","CRSArrTime","UniqueCarrier","FlightNum","CRSElapsedTime","AirTime","Origin","Dest","Distance")

#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
doJavapredictTest(model = "glm", training_frame = training_frame, test_file = test_file, test_frame = test_frame,
                  y = y,
                  x = x,
                  family = "binomial")

PASS()
