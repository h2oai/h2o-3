# An example of binary classification on a local machine using h2o.stack

library(h2oEnsemble)  # Requires version >=0.1.8 of h2oEnsemble
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


# The h2o.stack function is an alternative to the h2o.ensemble function, which
# allows the user to specify H2O models individually and then stack them together
# at a later time.  Saved models, re-loaded from disk, can also be stacked.

# The base models must use identical cv folds; this can be achieved in two ways:
# 1. they be specified explicitly by using the fold_column argument, or
# 2. use same value for `nfolds` and set `fold_assignment = "Modulo"`

nfolds <- 5  

glm1 <- h2o.glm(x = x, y = y, family = family, 
                training_frame = train,
                nfolds = nfolds,
                fold_assignment = "Modulo",
                keep_cross_validation_predictions = TRUE)

gbm1 <- h2o.gbm(x = x, y = y, distribution = "bernoulli",
                training_frame = train,
                seed = 1,
                nfolds = nfolds,
                fold_assignment = "Modulo",
                keep_cross_validation_predictions = TRUE)

rf1 <- h2o.randomForest(x = x, y = y, #distribution = "bernoulli",
                        training_frame = train,
                        seed = 1,
                        nfolds = nfolds,
                        fold_assignment = "Modulo",
                        keep_cross_validation_predictions = TRUE)

dl1 <- h2o.deeplearning(x = x, y = y, distribution = "bernoulli",
                        training_frame = train,
                        nfolds = nfolds,
                        fold_assignment = "Modulo",
                        keep_cross_validation_predictions = TRUE)

models <- list(glm1, gbm1, rf1, dl1)
metalearner <- "h2o.glm.wrapper"

stack <- h2o.stack(models = models,
                   response_frame = train[,y],
                   metalearner = metalearner, 
                   seed = 1,
                   keep_levelone_data = TRUE)


# Compute test set performance:
perf <- h2o.ensemble_performance(stack, newdata = test)

print(perf)
#Base learner performance, sorted by metric:
#                                   learner       AUC
#1          GLM_model_R_1459208808410_23420 0.6870772
#4 DeepLearning_model_R_1459208808410_25042 0.7167811
#3          DRF_model_R_1459208808410_24305 0.7697072
#2          GBM_model_R_1459208808410_23438 0.7817096
#
#
#H2O Ensemble Performance on <newdata>:
#----------------
#Family: binomial
#
#Ensemble performance (AUC): 0.787814391608448
