# An example of binary classification using h2o.ensemble
# This example is also included in the R package documentation for h2o.ensemble


library(h2oEnsemble)  # Requires version >=0.0.4 of h2oEnsemble
library(SuperLearner)  # For metalearner such as "SL.glm"
library(cvAUC)  # Used to calculate test set AUC (requires version >=1.0.1 of cvAUC)
localH2O <-  h2o.init(nthreads = -1)  # Start an H2O cluster with nthreads = num cores on your machine


# Import a sample binary outcome train/test set into R
train <- read.table("http://www.stat.berkeley.edu/~ledell/data/higgs_10k.csv", sep=",")
test <- read.table("http://www.stat.berkeley.edu/~ledell/data/higgs_test_5k.csv", sep=",")


# Convert R data.frames into H2O parsed data objects
training_frame <- as.h2o(localH2O, train)
validation_frame <- as.h2o(localH2O, test)
y <- "V1"
x <- setdiff(names(training_frame), y)
family <- "binomial"
training_frame[,c(y)] <- as.factor(training_frame[,c(y)])  #Force Binary classification
validation_frame[,c(y)] <- as.factor(validation_frame[,c(y)])  # check to validate that this guarantees the same 0/1 mapping?


# Specify the base learner library & the metalearner
learner <- c("h2o.glm.wrapper", "h2o.randomForest.wrapper", 
             "h2o.gbm.wrapper", "h2o.deeplearning.wrapper")
metalearner <- "SL.glm"


# Train the ensemble using 5-fold CV to generate level-one data
# More CV folds will take longer to train, but should increase performance
fit <- h2o.ensemble(x = x, y = y, 
                    training_frame = training_frame, 
                    family = family, 
                    learner = learner, 
                    metalearner = metalearner,
                    cvControl = list(V = 5, shuffle = TRUE))


# Generate predictions on the test set
pred <- predict.h2o.ensemble(fit, validation_frame)
labels <- as.data.frame(validation_frame[,c(y)])[,1]


# Ensemble test AUC 
AUC(predictions=as.data.frame(pred$pred)[,1], labels=labels)
# 0.7889155


# Base learner test AUC (for comparison)
L <- length(learner)
sapply(seq(L), function(l) AUC(predictions = as.data.frame(pred$basepred)[,l], labels = labels)) 
# 0.6871342 0.7743299 0.7816997 0.7344790

# Note that the ensemble results above are not reproducible since 
# h2o.deeplearning is not reproducible when using multiple cores.
