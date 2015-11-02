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
  model_id = "", validation_frame = NULL,
  family = c("AUTO", "binomial", "gaussian"),
  learner = c("h2o.glm.wrapper", "h2o.randomForest.wrapper", "h2o.gbm.wrapper", "h2o.deeplearning.wrapper"), 
  metalearner = "h2o.glm.wrapper", 
  cvControl = list(V = 5, shuffle = TRUE), 
  seed = 1, parallel = "seq", 
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
An H2O Frame object containing the variables in the model.
}
  \item{family}{
A description of the error distribution and link function to be used in the model.  This must be a character string.  Currently supports \code{"binomial"} and \code{"gaussian"}.  
}
  \item{model_id}{
(Optional) The unique id assigned to the resulting model. If none is given, an id will automatically be generated.
}
  \item{validation_frame}{
(Optional) An H2O Frame object indicating the validation dataset used to contruct the confusion matrix. If left blank, this defaults to the training data when \code{nfolds = 0}.  Currently not used.
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
  Logical, defaults to \code{TRUE}.  Keep the \code{levelone} H2O Frame of cross-validated predicted values (Z matrix) and original response vector, y (cbind to Z).
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
An H2O Frame object.  The \code{levelone} H2O Frame includes the "Z matrix" (the cross-validated predicted values for each base learner) and original response vector, y.  In the stacking ensemble literature, the Z matrix is the design matrix used to train the metalearner.
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
\code{\link[SuperLearner:SuperLearner]{SuperLearner}}, \code{\link[subsemble:subsemble]{subsemble}}
}
\examples{
\dontrun{
    
# An example of binary classification on a local machine using h2o.ensemble

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

}
}
