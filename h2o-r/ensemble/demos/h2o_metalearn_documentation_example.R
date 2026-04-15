# An example of binary classification on a local machine using h2o.ensemble & h2o.metalearn

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
# Let's use a reproducible library (set seed on RF and GBM):
h2o.randomForest.1 <- function(..., ntrees = 100, seed = 1) h2o.randomForest.wrapper(..., ntrees = ntrees, seed = seed)
h2o.gbm.1 <- function(..., ntrees = 100, seed = 1) h2o.gbm.wrapper(..., ntrees = ntrees, seed = seed)
learner <- c("h2o.glm.wrapper", "h2o.randomForest.1", "h2o.gbm.1")
metalearner <- "h2o.glm.wrapper"


# Train the ensemble using 10-fold CV to generate level-one data
# More CV folds will take longer to train, but should increase performance
fit <- h2o.ensemble(x = x, y = y, 
                    training_frame = train, 
                    family = family, 
                    learner = learner, 
                    metalearner = metalearner,
                    cvControl = list(V = 10, shuffle = TRUE))

# Compute test set performance:
perf <- h2o.ensemble_performance(fit, newdata = test)
print(perf, metric = "AUC")

# Base learner performance, sorted by specified metric:
#              learner       AUC
# 1    h2o.glm.wrapper 0.6871334
# 2 h2o.randomForest.1 0.7785505
# 3          h2o.gbm.1 0.7803885
# 
# 
# H2O Ensemble Performance on <newdata>:
# ----------------
# Family: binomial
# 
# Ensemble performance (AUC): 0.786960998427388


# Now let's re-train the metalearner fit to see if we get better performance.
# Previously, we used a GLM metalearner, and now we will try a non-negative GLM.

h2o.glm_nn <- function(..., non_negative = TRUE) h2o.glm.wrapper(..., non_negative = non_negative)
newfit <- h2o.metalearn(fit, metalearner = "h2o.glm_nn")

# Compute test set performance:
newperf <- h2o.ensemble_performance(newfit, newdata = test)
print(newperf, metric = "AUC") 

# Base learner performance, sorted by specified metric:
#              learner       AUC
# 1    h2o.glm.wrapper 0.6871334
# 2 h2o.randomForest.1 0.7785505
# 3          h2o.gbm.1 0.7803885
# 
# 
# H2O Ensemble Performance on <newdata>:
# ----------------
# Family: binomial
# 
# Ensemble performance (AUC): 0.786998403256231

# Ok, so the non-negative restriction improved the results, but not by much in this case.


# Next we will try a GBM (defined above) for a metalearner.
newfit <- h2o.metalearn(fit, metalearner = "h2o.gbm.1")

# Compute test set performance:
newperf <- h2o.ensemble_performance(newfit, newdata = test)
print(newperf, metric = "AUC") 

# Base learner performance, sorted by specified metric:
#              learner       AUC
# 1    h2o.glm.wrapper 0.6871334
# 2 h2o.randomForest.1 0.7785505
# 3          h2o.gbm.1 0.7803885
# 
# 
# H2O Ensemble Performance on <newdata>:
# ----------------
# Family: binomial
# 
# Ensemble performance (AUC): 0.780514980030648


# We see that on this dataset & base learner combination,
# that an ensemble with a GLM metalearner actually performs better, 
# in terms of test set AUC, than an ensemble with a GBM metalearner.
# Typically tree-based methods don't work as well as metalearners.


# Now let's re-train the metalearner again a Deep Neural Net.
newfit <- h2o.metalearn(fit, metalearner = "h2o.deeplearning.wrapper")

# Compute test set performance:
newperf <- h2o.ensemble_performance(newfit, newdata = test)
print(newperf, metric = "AUC") 


# Base learner performance, sorted by specified metric:
#              learner       AUC
# 1    h2o.glm.wrapper 0.6871334
# 2 h2o.randomForest.1 0.7785505
# 3          h2o.gbm.1 0.7803885
# 
# 
# H2O Ensemble Performance on <newdata>:
# ----------------
# Family: binomial
# 
# Ensemble performance (AUC): 0.786774296045143

# Here we have performance similar to a GLM.  
# It's a good idea to always try at least a GLM and DNN.

