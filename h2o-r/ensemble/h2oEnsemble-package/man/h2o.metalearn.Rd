\name{h2o.metalearn}
\alias{h2o.metalearn}
\title{
H2O Metalearn
}
\description{
Re-trains an existing H2O Ensemble fit using a new metalearning function.
}
\usage{
h2o.metalearn(object, 
  metalearner = "h2o.glm.wrapper",
  seed = 1,
  keep_levelone_data = TRUE)
}
\arguments{
  \item{object}{
An object of class, "h2o.ensemble".
}
  \item{metalearner}{
A string specifying the prediction algorithm used to learn the optimal combination of the base learners.  Supports both h2o and SuperLearner wrapper functions.
}
  \item{seed}{
A random seed to be set (integer); defaults to 1. If \code{NULL}, then a random seed will not be set.  The seed is set prior to creating the CV folds and prior to model training for base learning and metalearning.
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
An H2OFrame object.  The levelone H2OFrame includes the Z matrix (the cross-validated predicted values for each base learner), fold id column and original response vector, y.  In the stacking ensemble literature, the Z matrix is the design matrix used to train the metalearner.
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
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.ensemble}}
}
\examples{
\dontrun{
    
# An example of binary classification on a local machine using h2o.ensemble

library(h2oEnsemble)  # Requires version >=0.1.7 of h2oEnsemble
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


}
}
