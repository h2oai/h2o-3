### Lending Club example using cleaned up dataset & h2o.ensemble ###

library(h2o)
h2o.init(nthreads = -1, max_mem_size = "8G")

# Import the data
loan_csv <- "https://raw.githubusercontent.com/h2oai/app-consumer-loan/master/data/loan.csv"
data <- h2o.importFile(loan_csv)  # 163994 x 15
data$bad_loan <- as.factor(data$bad_loan)  #encode the binary repsonse as a factor

# Partition the data into train and test sets
splits <- h2o.splitFrame(data, seed = 1)
train <- splits[[1]]
test <- splits[[2]]

# Identify response and predictor variables
y <- "bad_loan"
x <- setdiff(names(data), c(y, "int_rate"))

library(h2oEnsemble)
# Specify the base learner library & the metalearner
learner <- c("h2o.glm.wrapper", "h2o.randomForest.wrapper", 
             "h2o.gbm.wrapper", "h2o.deeplearning.wrapper")
metalearner <- "h2o.glm.wrapper"
family <- "binomial"


# Train the ensemble using 5-fold CV to generate level-one data
# More CV folds will take longer to train, but should increase performance
fit <- h2o.ensemble(x = x, y = y, 
                    training_frame = train,
                    family = family, 
                    learner = learner, 
                    metalearner = metalearner,
                    cvControl = list(V = 5, shuffle = TRUE))

# Evaluate performance on a test set
perf <- h2o.ensemble_performance(fit, newdata = test)
perf

# Base learner performance, sorted by specified metric:
#                    learner       AUC
# 2 h2o.randomForest.wrapper 0.6613592
# 1          h2o.glm.wrapper 0.6777797
# 4 h2o.deeplearning.wrapper 0.6788880
# 3          h2o.gbm.wrapper 0.6832562
# 
# 
# H2O Ensemble Performance on <newdata>:
# ----------------
# Family: binomial
# 
# Ensemble performance (AUC): 0.686066263066993



# Now try metalearning with non-negative weights to see if that helps
h2o.glm_nn <- function(..., non_negative = TRUE) {
  h2o.glm.wrapper(..., non_negative = non_negative)
}
metalearner <- "h2o.glm_nn"
fit <- h2o.metalearn(fit, metalearner)
perf <- h2o.ensemble_performance(fit, newdata = test)
# Re-test ensemble AUC
h2o.auc(perf$ensemble)
# [1] 0.686091   # very slight improvement...


# Try a DL metalearner
metalearner <- "h2o.deeplearning.wrapper"
fit <- h2o.metalearn(fit, metalearner)
perf <- h2o.ensemble_performance(fit, newdata = test)
# Re-test ensemble AUC
h2o.auc(perf$ensemble)
# [1] 0.6856208  #not as good as a linear metalearner



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
metalearner <- "h2o.glm_nn"
family <- "binomial"

# Train the ensemble using 5-fold CV to generate level-one data
# More CV folds will take longer to train, but should increase performance
fit <- h2o.ensemble(x = x, y = y, 
                    training_frame = train,
                    family = family, 
                    learner = learner, 
                    metalearner = metalearner,
                    cvControl = list(V = 5, shuffle = TRUE))


# Evaluate performance on a test set
perf <- h2o.ensemble_performance(fit, newdata = test)
perf


# Base learner performance, sorted by specified metric:
#               learner       AUC
# 14 h2o.deeplearning.2 0.6410442
# 6  h2o.randomForest.3 0.6666520
# 5  h2o.randomForest.2 0.6691613
# 9           h2o.gbm.3 0.6702904
# 4  h2o.randomForest.1 0.6708258
# 13 h2o.deeplearning.1 0.6761333
# 2           h2o.glm.2 0.6777797
# 3           h2o.glm.3 0.6778070
# 1           h2o.glm.1 0.6778252
# 15 h2o.deeplearning.3 0.6820867
# 11          h2o.gbm.5 0.6830643
# 10          h2o.gbm.4 0.6833709
# 12          h2o.gbm.6 0.6836920
# 8           h2o.gbm.2 0.6837584
# 7           h2o.gbm.1 0.6839902
# 
# 
# H2O Ensemble Performance on <newdata>:
# ----------------
# Family: binomial
# 
# Ensemble performance (AUC): 0.68690013373787

# We see here that ensemble performance does increase 
# by adding additional models to the ensemble. 
# Test set AUC went from 0.686 to 0.687
