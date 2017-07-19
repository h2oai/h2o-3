\name{h2o.ensemble}
\alias{h2o.ensemble}
\title{
H2O Ensemble
}
\description{
This function creates a "Super Learner" (stacking) ensemble using the H2O base learning algorithms specified by the user.
}
\usage{
h2o.ensemble(x, y, training_frame,  
  model_id = NULL, validation_frame = NULL,
  family = c("AUTO", "binomial", "gaussian", "quasibinomial", 
             "poisson", "gamma", "tweedie", 
             "laplace", "quantile", "huber"),
  learner = c("h2o.glm.wrapper", "h2o.randomForest.wrapper",
              "h2o.gbm.wrapper", "h2o.deeplearning.wrapper"), 
  metalearner = "h2o.glm.wrapper", 
  cvControl = list(V = 5, shuffle = TRUE), 
  seed = 1, 
  parallel = "seq", 
  keep_levelone_data = TRUE)
}
\arguments{
  \item{x}{
A vector containing the names of the predictors in the model.
}
  \item{y}{
The name of the response variable in the model.
}
  \item{training_frame}{
An H2OFrame object containing the variables in the model.
}
  \item{family}{
A description of the distribution function.  This must be a character string.  Defaults to \code{"AUTO"}, which means that it will use \code{"binomial"} for binary classification and \code{"gaussian"} for regression (determined by the column type of the response; a "numeric" response forces regression).  Must be one of: \code{"AUTO"}, \code{"binomial"}, \code{"gaussian"}, \code{"quasibinomial"}, \code{"poisson"}, \code{"gamma"}, \code{"tweedie"}, \code{"laplace"}, \code{"quantile"}, \code{"huber"}.  Binary classification requires \code{"binomial"}, and the rest are options for regression.  This will be passed on to the base learners, but if the base learner in a regression problem does not support the given distribution, then \code{"gaussian"} (default for regression) will be used instead. 
}
  \item{model_id}{
(Optional) The unique id assigned to the resulting model. If none is given, an id will automatically be generated.
}
  \item{validation_frame}{
(Optional) An H2OFrame object indicating the validation dataset used to contruct the confusion matrix. If left blank, this defaults to the training data when \code{nfolds = 0}.  Currently not used.
}
  \item{learner}{
A string or character vector naming the prediction algorithm(s) used to train the base models for the ensemble.  The functions must have the same format as the h2o wrapper functions.
}
  \item{metalearner}{
A string specifying the prediction algorithm used to learn the optimal combination of the base learners.  Supports both h2o and SuperLearner wrapper functions.
}
  \item{cvControl}{
A list of parameters to control the cross-validation process. The \code{V} parameter is an integer representing the number of cross-validation folds and defaults to 10. Other parmeters are \code{stratifyCV} and \code{shuffle}, which are not yet enabled. 
}
  \item{seed}{
A random seed to be set (integer); defaults to 1. If \code{NULL}, then a random seed will not be set.  The seed is set prior to creating the CV folds and prior to model training for base learning and metalearning.
}
  \item{parallel}{
A character string specifying optional parallelization. Use \code{"seq"} for sequential computation (the default) of the cross-validation and base learning steps. Use \code{"multicore"} to perform the V-fold (internal) cross-validation step as well as the final base learning step in parallel over all available cores. Or parallel can be a snow cluster object. Both parallel options use the built-in functionality of the R core "parallel" package.  Currently, only \code{"seq"} is compatible with the parallelized H2O algorithms, so this argument may be removed or modified in the future.
}
\item{keep_levelone_data}{
  Logical, defaults to \code{TRUE}.  Keep the \code{levelone} H2OFrame of cross-validated predicted values (Z matrix) and original response vector, y (cbind to Z).
}
}

\value{

\item{x}{
A vector containing the names of the predictors in the model.
}
\item{y}{
The name of the response variable in the model.
}
\item{family}{
Returns the \code{family} argument from above.  
}
\item{cvControl}{
Returns the \code{cvControl} argument from above.
}
\item{folds}{
A vector of fold ids for each observation, ordered by row index.  The number of unique fold ids is specified in \code{cvControl$V}.   
}
\item{ylim}{
Returns range of \code{y}.
}
\item{seed}{
An integer. Returns \code{seed} argument from above.
}
\item{parallel}{
An character vector. Returns \code{character} argument from above.
}
\item{basefits}{
A list of H2O models, each of which are trained using the \code{data} object.  The length of this list is equal to the number of base learners in the \code{learner} argument.
}
\item{metafit}{
The predictive model which is learned by regressing \code{y} on \code{Z} (see description of \code{Z} below).  The type of model is specified using the \code{metalearner} argument.
}
\item{levelone}{
An H2OFrame object.  The \code{levelone} H2OFrame includes the "Z matrix" (the cross-validated predicted values for each base learner) and original response vector, y.  In the stacking ensemble literature, the Z matrix is the design matrix used to train the metalearner.
}
\item{runtime}{
A list of runtimes for various steps of the algorithm.  The list contains \code{cv}, \code{metalearning}, \code{baselearning} and \code{total} elements.  The \code{cv} element is the time it takes to create the \code{Z} matrix (see above).  The \code{metalearning} element is the training time for the metalearning step.  The \code{baselearning} element is a list of training times for each of the models in the ensemble.  The time to run the entire \code{h2o.ensemble} function is given in \code{total}.
}
\item{h2o_version}{
The version of the h2o R package.
}
\item{h2oEnsemble_version}{
The version of the h2oEnsemble R package.
}
}
\references{
\href{https://github.com/h2oai/h2o-3/tree/master/h2o-r/ensemble}{H2O Ensemble Homepage}	\cr
\href{https://github.com/h2oai/h2o-tutorials/blob/master/tutorials/ensembles-stacking/h2oEnsemble_tutorial.md}{H2O Ensemble Tutorial}	\cr
\cr	
LeDell, E. (2015) Scalable Ensemble Learning and Computationally Efficient Variance Estimation (Doctoral Dissertation).  University of California, Berkeley, USA.\cr
\url{http://www.stat.berkeley.edu/~ledell/papers/ledell-phd-thesis.pdf}\cr
\cr
van der Laan, M. J., Polley, E. C. and Hubbard, A. E. (2007) Super Learner, Statistical Applications of Genetics and Molecular Biology, 6, article 25. \cr
\url{http://dx.doi.org/10.2202/1544-6115.1309}\cr
\url{http://biostats.bepress.com/ucbbiostat/paper222}\cr
\cr
Breiman, L. (1996) Stacked Regressions, Machine Learning, 24:49-64.\cr
\url{http://dx.doi.org/10.1007/BF00117832}\cr
\url{http://statistics.berkeley.edu/sites/default/files/tech-reports/367.pdf}
}
\author{
Erin LeDell \email{erin@h2o.ai}
}


\seealso{
\code{\link[h2oEnsemble:h2o.ensemble_performance]{h2o.ensemble_performance}}
}
\examples{
\dontrun{
    
# An example of binary classification on a local machine using h2o.ensemble

library(h2oEnsemble)
h2o.init() # Start the H2O cluster


# Import a sample binary outcome train/test set into R
train <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_train_5k.csv")
test <- h2o.importFile("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")
y <- "response"
x <- setdiff(names(train), y)
family <- "binomial"

#For binary classification, response should be a factor
train[,y] <- as.factor(train[,y])  
test[,y] <- as.factor(test[,y])


# Specify the base learner library & the metalearner
learner <- c("h2o.glm.wrapper", "h2o.randomForest.wrapper", 
               "h2o.gbm.wrapper", "h2o.deeplearning.wrapper")
metalearner <- "h2o.glm.wrapper"


# Train the ensemble using 5-fold CV (the default) to generate level-one data
# More CV folds will take longer to train, but should increase performance
fit <- h2o.ensemble(x = x, y = y, 
                    training_frame = train, 
                    family = family, 
                    learner = learner, 
                    metalearner = metalearner)


# Compute test set performance:
perf <- h2o.ensemble_performance(fit, newdata = test)

# The "perf" object has a print method, so we can print default metric by typing: perf
perf  # prints the following

# Base learner performance, sorted by specified metric:
#                    learner       AUC
# 1          h2o.glm.wrapper 0.6824304
# 4 h2o.deeplearning.wrapper 0.7006335
# 2 h2o.randomForest.wrapper 0.7570211
# 3          h2o.gbm.wrapper 0.7780807
# 
# 
# H2O Ensemble Performance on <newdata>:
# ----------------
# Family: binomial
# 
# Ensemble performance (AUC): 0.781580655670451

# To print the results for a particular metric, like MSE, do the following:
print(perf, metric = "MSE")

# Base learner performance, sorted by specified metric:
#                    learner       MSE
# 4 h2o.deeplearning.wrapper 0.2305775
# 1          h2o.glm.wrapper 0.2225176
# 2 h2o.randomForest.wrapper 0.2014339
# 3          h2o.gbm.wrapper 0.1916273
# 
# 
# H2O Ensemble Performance on <newdata>:
# ----------------
# Family: binomial
# 
# Ensemble performance (MSE): 0.1898735479034431

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



# If desired, you can generate predictions on the test set (as an H2OFrame)
pp <- predict(fit, test)

# This is useful if you need to calculate custom performance metrics in R (not provided by H2O)
# To do so, you can pull the predictions and labels into local R memory
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

}
}
