# An example of binary classification on a local machine using h2o.ensemble

library(h2oEnsemble)  # Requires version >=0.1.7 of h2oEnsemble
h2o.init(nthreads = -1)  # Start an H2O cluster with nthreads = num cores on your machine


# Import a sample binary outcome train/test set into R
train <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/higgs/higgs_train_5k.csv")
test <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/higgs/higgs_test_5k.csv")
y <- "response"
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


# Compute test set performance:
perf <- h2o.ensemble_performance(fit, newdata = test)

# The "perf" object has a print method, so we can print results (for the default metric) by simply typing: perf
perf  #prints the following
# Base learner performance, sorted by specified metric:
#                   learner       AUC
# 1          h2o.glm.wrapper 0.6871334
# 4 h2o.deeplearning.wrapper 0.7261466
# 2 h2o.randomForest.wrapper 0.7655972
# 3          h2o.gbm.wrapper 0.7817096
# 
# 
# H2O Ensemble Performance on <newdata>:
# ----------------
# Family: binomial
# 
# Ensemble performance (AUC): 0.784785565758091

# To print the results for a particular metric, like MSE, do the following:
print(perf, metric = "MSE")

# Base learner performance, sorted by specified metric:
#                    learner       MSE
# 1          h2o.glm.wrapper 0.2216862
# 4 h2o.deeplearning.wrapper 0.2182509
# 2 h2o.randomForest.wrapper 0.1981485
# 3          h2o.gbm.wrapper 0.1900497
# 
# 
# H2O Ensemble Performance on <newdata>:
# ----------------
# Family: binomial
# 
# Ensemble performance (MSE): 0.189215219642971

# By calculating the base learner test set metrics, we can compare the performance
# of the ensemble to the top base learner - GBM is the top base learner, both by AUC and MSE.

# Note that the ensemble results above are not reproducible since 
# h2o.deeplearning is not reproducible when using multiple cores,
# and we did not set a seed for h2o.randomForest.wrapper or h2o.gbm.wrapper.


# To access results directly: 

# Ensemble test set AUC
perf$ensemble@metrics$AUC
# [1] 0.7847856

# Base learner test set AUC (for comparison)
L <- length(learner)
auc <- sapply(seq(L), function(l) perf$base[[l]]@metrics$AUC)
data.frame(learner, auc)
#                    learner       auc
# 1          h2o.glm.wrapper 0.6871334
# 2 h2o.randomForest.wrapper 0.7655972
# 3          h2o.gbm.wrapper 0.7817096
# 4 h2o.deeplearning.wrapper 0.7261466



# If desired, you can generate predictions on the test set
# This is useful if you need to calculate custom performance metrics in R (not provided by H2O)
pp <- predict(fit, test)
predictions <- as.data.frame(pp$pred)[,3]  #third column, p1 is P(Y==1)
labels <- as.data.frame(test[,y])[,1]


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
