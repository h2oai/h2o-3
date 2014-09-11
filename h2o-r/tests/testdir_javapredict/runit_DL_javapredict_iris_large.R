#----------------------------------------------------------------------
# Purpose:  This test exercises the DeepLearning model downloaded as java code
#           for the iris data set.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------

options(echo=FALSE)
TEST_ROOT_DIR <- ".."
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source(paste(TEST_ROOT_DIR, "findNSourceUtils.R", sep="/"))


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

train <- locate("smalldata/iris/iris_train.csv")
test <- locate("smalldata/iris/iris_test.csv")
x = c("sepal_len","sepal_wid","petal_len","petal_wid");
y = "species"
classification = T
activation = "Tanh"
epochs = 2

#----------------------------------------------------------------------
# Run the tests
#----------------------------------------------------------------------

# large network
hidden = c(500,500,500)
source('../Utils/shared_javapredict_DL.R')

# with imbalance correction
hidden = c(13,17,50,3)
balance_classes = T
source('../Utils/shared_javapredict_DL.R')

# without imbalance correction
balance_classes = F
source('../Utils/shared_javapredict_DL.R')

# other activation functions
activation = "TanhWithDropout"
source('../Utils/shared_javapredict_DL.R')

activation = "Rectifier"
source('../Utils/shared_javapredict_DL.R')

activation = "RectifierWithDropout"
source('../Utils/shared_javapredict_DL.R')

activation = "Maxout"
source('../Utils/shared_javapredict_DL.R')

activation = "MaxoutWithDropout"
source('../Utils/shared_javapredict_DL.R')

# regression
classification = F
activation = "Tanh"
x = c("species","sepal_len","sepal_wid","petal_len")
y = c("petal_wid")
source('../Utils/shared_javapredict_DL.R')

# ignore a column
x = c("species","sepal_wid","petal_len")
source('../Utils/shared_javapredict_DL.R')

# other activation functions
activation = "TanhWithDropout"
source('../Utils/shared_javapredict_DL.R')

activation = "Rectifier"
source('../Utils/shared_javapredict_DL.R')

activation = "RectifierWithDropout"
source('../Utils/shared_javapredict_DL.R')

activation = "Maxout"
source('../Utils/shared_javapredict_DL.R')

activation = "MaxoutWithDropout"
source('../Utils/shared_javapredict_DL.R')
