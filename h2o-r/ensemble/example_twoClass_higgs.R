# An example of binary classification using h2o.ensemble
# This example is also included in the R package documentation for h2o.ensemble


library(h2oEnsemble)  # Requires version >=0.0.4 of h2oEnsemble
library(cvAUC)  # Used to calculate test set AUC (requires version >=1.0.1 of cvAUC)
localH2O <-  h2o.init(nthreads = -1)  # Start an H2O cluster with nthreads = num cores on your machine


# Import a sample binary outcome train/test set into R
train <- h2o.importFile("http://www.stat.berkeley.edu/~ledell/data/higgs_10k.csv")
test <- h2o.importFile("http://www.stat.berkeley.edu/~ledell/data/higgs_test_5k.csv")
y <- "C1"
x <- setdiff(names(train), y)
family <- "binomial"

#For binary classification, response should be a factor
train[,y] <- as.factor(train[,y])  
test[,y] <- as.factor(test[,y])


# Specify the base learner library & the metalearner
learner <- c("h2o.glm.wrapper", "h2o.randomForest.wrapper", 
             "h2o.gbm.wrapper", "h2o.deeplearning.wrapper")
metalearner <- "h2o.deeplearning.wrapper"


# Train the ensemble using 5-fold CV to generate level-one data
# More CV folds will take longer to train, but should increase performance
fit <- h2o.ensemble(x = x, y = y, 
                    training_frame = train, 
                    family = family, 
                    learner = learner, 
                    metalearner = metalearner,
                    cvControl = list(V = 5, shuffle = TRUE))


# Generate predictions on the test set
pp <- predict(fit, test)
predictions <- as.data.frame(pp$pred)[,3]  #third column, p1 is P(Y==1)
labels <- as.data.frame(test[,y])[,1]


# Ensemble test AUC 
cvAUC::AUC(predictions = predictions, labels = labels)
# 0.7888723


# Base learner test AUC (for comparison)
L <- length(learner)
auc <- sapply(seq(L), function(l) cvAUC::AUC(predictions = as.data.frame(pp$basepred)[,l], labels = labels)) 
data.frame(learner, auc)
#                   learner       auc
#1          h2o.glm.wrapper 0.6871288
#2 h2o.randomForest.wrapper 0.7711654
#3          h2o.gbm.wrapper 0.7817075
#4 h2o.deeplearning.wrapper 0.7425813

# Note that the ensemble results above are not reproducible since 
# h2o.deeplearning is not reproducible when using multiple cores,
# and we did not set a seed for h2o.randomForest.wrapper or h2o.gbm.wrapper.

# Additional note: In a future version, performance metrics such as AUC
# will be computed automatically, as in the other H2O algos.

# Here is an example of how to generate a base learner library using custom base learners:
h2o.randomForest.1 <- function(..., ntrees = 1000, nbins = 100, seed = 1) {
  h2o.randomForest.wrapper(..., ntrees = ntrees, nbins = nbins, seed = seed)
}
h2o.deeplearning.1 <- function(..., hidden = c(500,500), activation = "Rectifier", seed = 1) {
  h2o.deeplearning.wrapper(..., hidden = hidden, activation = activation, seed = seed)
}
h2o.deeplearning.2 <- function(..., hidden = c(200,200,200), activation = "Tanh", seed = 1) {
  h2o.deeplearning.wrapper(..., hidden = hidden, activation = activation, seed = seed)
}
learner <- c("h2o.randomForest.1", "h2o.deeplearning.1", "h2o.deeplearning.2")
