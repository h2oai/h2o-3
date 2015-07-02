# An example of binary classification using h2o.ensemble
# This example is also included in the R package documentation for h2o.ensemble

library(h2oEnsemble)
library(SuperLearner)  # For metalearner such as "SL.glm"
library(cvAUC)  # Used to calculate test set AUC (requires version >=1.0.1 of cvAUC)
localH2O <-  h2o.init(ip = "localhost", port = 54321, startH2O = TRUE, nthreads = -1)


# Import a sample binary outcome train/test set into R
train <- read.table("http://www.stat.berkeley.edu/~ledell/data/higgs_5k.csv", sep=",")
test <- read.table("http://www.stat.berkeley.edu/~ledell/data/higgs_test_5k.csv", sep=",")


# Convert R data.frames into H2O parsed data objects
data <- as.h2o(localH2O, train)
newdata <- as.h2o(localH2O, test)
y <- "V1"
x <- setdiff(names(data), y)
family <- "binomial"


# Create a custom base learner library & specify the metalearner
h2o.randomForest.1 <- function(..., ntrees = 1000, nbins = 100, seed = 1) h2o.randomForest.wrapper(..., ntrees = ntrees, nbins = nbins, seed = seed)
h2o.deeplearning.1 <- function(..., hidden = c(500,500), activation = "Rectifier", seed = 1)  h2o.deeplearning.wrapper(..., hidden = hidden, activation = activation, seed = seed)
h2o.deeplearning.2 <- function(..., hidden = c(200,200,200), activation = "Tanh", seed = 1)  h2o.deeplearning.wrapper(..., hidden = hidden, activation = activation, seed = seed)
learner <- c("h2o.randomForest.1", "h2o.deeplearning.1", "h2o.deeplearning.2")
metalearner <- c("SL.glm")


# Train the ensemble using 4-fold CV to generate level-one data
# More CV folds will take longer to train, but should increase performance
fit <- h2o.ensemble(x = x, y = y, data = data, family = family, 
                    learner = learner, metalearner = metalearner,
                    cvControl = list(V=4))


# Generate predictions on the test set
pred <- predict(fit, newdata)
labels <- as.data.frame(newdata[,c(y)])[,1]


# Ensemble test AUC 
AUC(predictions=as.data.frame(pred$pred)[,1], labels=labels)
# 0.7681649


# Base learner test AUC (for comparison)
L <- length(learner)
sapply(seq(L), function(l) AUC(predictions = as.data.frame(pred$basepred)[,l], labels = labels)) 
# 0.7583084 0.7145333 0.7123253


# Note that the ensemble results above are not reproducible since 
# h2o.deeplearning is not reproducible when using multiple cores.
# For reproducible results, pass reproducible=TRUE to h2o.deeplearning()
