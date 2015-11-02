### Lending Club example using cleaned up dataset & h2o.ensemble ###

library(h2o)
h2o.init(nthreads = -1, max_mem_size = "8G")

loan_csv <- "https://raw.githubusercontent.com/h2oai/app-consumer-loan/master/data/loan.csv"

data <- h2o.importFile(loan_csv)  # 163994 x 15
data$bad_loan <- as.factor(data$bad_loan)
rand  <- h2o.runif(data, seed = 1)
train <- data[rand$rnd <= 0.8, ]
valid <- data[rand$rnd > 0.8, ]

y <- "bad_loan"
x <- setdiff(names(data), c(y, "int_rate"))

library(h2oEnsemble)
# Specify the base learner library & the metalearner
learner <- c("h2o.glm.wrapper", "h2o.randomForest.wrapper", 
             "h2o.gbm.wrapper", "h2o.deeplearning.wrapper")
metalearner <- "h2o.deeplearning.wrapper"
family <- "binomial"


# Train the ensemble using 5-fold CV to generate level-one data
# More CV folds will take longer to train, but should increase performance
fit <- h2o.ensemble(x = x, y = y, 
                    training_frame = train,
                    validation_frame = NULL,
                    family = family, 
                    learner = learner, 
                    metalearner = metalearner,
                    cvControl = list(V = 5, shuffle = TRUE))


# Generate predictions on the test set
pred <- predict(fit, valid)
predictions <- as.data.frame(pred$pred)[,3]  #third column, p1 is P(Y==1)
labels <- as.data.frame(valid[,c(y)])[,1]


# Ensemble test AUC 
cvAUC::AUC(predictions = predictions , labels = labels)
# 0.6802715


# Base learner test AUC (for comparison)
learner <- names(fit$basefits)
L <- length(learner)
auc <- sapply(seq(L), function(l) cvAUC::AUC(predictions = as.data.frame(pred$basepred)[,l], labels = labels)) 
data.frame(learner, auc)
#learner       auc
#1          h2o.glm.wrapper 0.6721662
#2 h2o.randomForest.wrapper 0.6673966
#3          h2o.gbm.wrapper 0.6737319
#4 h2o.deeplearning.wrapper 0.6696115




# Now let's try again with a more extensive set of base learners
# Here is an example of how to generate a custom learner wrappers:

h2o.glm.1 <- function(..., alpha = 0.0) h2o.glm.wrapper(..., alpha = alpha)
h2o.glm.2 <- function(..., alpha = 0.5) h2o.glm.wrapper(..., alpha = alpha)
h2o.glm.3 <- function(..., alpha = 1.0) h2o.glm.wrapper(..., alpha = alpha)
h2o.randomForest.1 <- function(..., ntrees = 200, nbins = 50, seed = 1) h2o.randomForest.wrapper(..., ntrees = ntrees, nbins = nbins, seed = seed)
h2o.randomForest.2 <- function(..., ntrees = 200, sample_rate = 0.75, seed = 1) h2o.randomForest.wrapper(..., ntrees = ntrees, sample_rate = sample_rate, seed = seed)
h2o.randomForest.3 <- function(..., ntrees = 200, sample_rate = 0.85, seed = 1) h2o.randomForest.wrapper(..., ntrees = ntrees, sample_rate = sample_rate, seed = seed)
h2o.gbm.1 <- function(..., ntrees = 100, nbins = 100, seed = 1) h2o.gbm.wrapper(..., ntrees = ntrees, nbins = nbins, seed = seed)
h2o.gbm.2 <- function(..., ntrees = 200, nbins = 50, seed = 1) h2o.gbm.wrapper(..., ntrees = ntrees, nbins = nbins, seed = seed)
h2o.gbm.3 <- function(..., ntrees = 100, max_depth = 10, seed = 1) h2o.gbm.wrapper(..., ntrees = ntrees, max_depth = max_depth, seed = seed)
h2o.gbm.4 <- function(..., ntrees = 100, col_sample_rate = 0.8, seed = 1) h2o.gbm.wrapper(..., ntrees = ntrees, col_sample_rate = col_sample_rate, seed = seed)
h2o.gbm.5 <- function(..., ntrees = 200, col_sample_rate = 0.8, seed = 1) h2o.gbm.wrapper(..., ntrees = ntrees, col_sample_rate = col_sample_rate, seed = seed)
h2o.gbm.6 <- function(..., ntrees = 200, col_sample_rate = 0.7, seed = 1) h2o.gbm.wrapper(..., ntrees = ntrees, col_sample_rate = col_sample_rate, seed = seed)
h2o.deeplearning.1 <- function(..., hidden = c(500,500), activation = "Rectifier", seed = 1)  h2o.deeplearning.wrapper(..., hidden = hidden, activation = activation, seed = seed)
h2o.deeplearning.2 <- function(..., hidden = c(200,200,200), activation = "Tanh", seed = 1)  h2o.deeplearning.wrapper(..., hidden = hidden, activation = activation, seed = seed)
h2o.deeplearning.3 <- function(..., hidden = c(500,500), activation = "RectifierWithDropout", seed = 1)  h2o.deeplearning.wrapper(..., hidden = hidden, activation = activation, seed = seed)


learner <- c("h2o.glm.1", "h2o.glm.2", "h2o.glm.3",
             "h2o.randomForest.1", "h2o.randomForest.2", "h2o.randomForest.3",
             "h2o.gbm.1", "h2o.gbm.2", "h2o.gbm.3", "h2o.gbm.4", "h2o.gbm.5", "h2o.gbm.6",
             "h2o.deeplearning.1", "h2o.deeplearning.2", "h2o.deeplearning.3")
metalearner <- "h2o.deeplearning.wrapper"
family <- "binomial"

# Train the ensemble using 5-fold CV to generate level-one data
# More CV folds will take longer to train, but should increase performance
fit <- h2o.ensemble(x = x, y = y, 
                    training_frame = train,
                    validation_frame = NULL,
                    family = family, 
                    learner = learner, 
                    metalearner = metalearner,
                    cvControl = list(V = 5, shuffle = TRUE))


# Generate predictions on the test set
pred <- predict.h2o.ensemble(fit, valid)
predictions <- as.data.frame(pred$pred)[,3]  #third column, p1 is P(Y==1)
labels <- as.data.frame(valid[,c(y)])[,1]


# Ensemble test AUC 
cvAUC::AUC(predictions = predictions , labels = labels)
# 0.6832164


# Base learner test AUC (for comparison)
L <- length(learner)
auc <- sapply(seq(L), function(l) cvAUC::AUC(predictions = as.data.frame(pred$basepred)[,l], labels = labels)) 
data.frame(learner, auc)
# learner       auc
# 1           h2o.glm.1 0.6742993
# 2           h2o.glm.2 0.6743306
# 3           h2o.glm.3 0.6743672
# 4  h2o.randomForest.1 0.6702020
# 5  h2o.randomForest.2 0.6698333
# 6  h2o.randomForest.3 0.6698533
# 7           h2o.gbm.1 0.6801405
# 8           h2o.gbm.2 0.6785209
# 9           h2o.gbm.3 0.6699785
# 10          h2o.gbm.4 0.6805663
# 11          h2o.gbm.5 0.6790248
# 12          h2o.gbm.6 0.6790248
# 13 h2o.deeplearning.1 0.6772474
# 14 h2o.deeplearning.2 0.6696069
# 15 h2o.deeplearning.3 0.6802065