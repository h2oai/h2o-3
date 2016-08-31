\name{h2o.metalearn_cv}
\alias{h2o.metalearn_cv}
\title{
H2O Metalearn Cross Validation
}
\description{
Re-trains an existing H2O Ensemble CV fit using a new metalearning function.
}
\usage{
h2o.metalearn_cv(object, 
  metalearner = "h2o.glm.wrapper",
  seed = 1,
  keep_levelone_data = TRUE)
}
\arguments{
  \item{object}{
An object of class, "h2o.ensemble_cv".
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
  \item{h2o.metalearn_cv object}{
A list of length \code{K*times}.  Each element contains the h2o.ensemble output for the specified metalearner and corresponding fold/repeat. Indices of the training set are stored as \code{tt_ind}.
}
}

\references{
Spencer Aiello, Tom Kraljevic, Petr Maj and with contributions from the H2O.ai team (2015). h2o: R Interface for H2O. R package version 3.8.1.3. \cr \url{https://CRAN.R-project.org/package=h2o}
\cr
Erin LeDell (2016). h2oEnsemble: H2O Ensemble Learning. R package version 0.1.6. \url{https://github.com/h2oai/h2o-3/tree/master/h2o-r/ensemble/h2oEnsemble-package}
}

\author{
Rebecca Z. Krouse, Agustin Calatroni 
}

\seealso{
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.ensemble_cv}},
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.performance_cv}}
}

\examples{
library(h2oEnsemble)
localH2O <-  h2o.init(nthreads = -1)

# Import a sample binary outcome train/test set into R
train <- h2o.importFile("http://www.stat.berkeley.edu/~ledell/data/higgs_10k.csv")
test  <- h2o.importFile("http://www.stat.berkeley.edu/~ledell/data/higgs_test_5k.csv")
y <- "C1"
x <- setdiff(names(train), y)
family <- "binomial"

#For binary classification, response should be a factor
train[,y] <- as.factor(train[,y])
test[,y]  <- as.factor( test[,y])

# Specify the base learner library & the metalearner
learner <- c("h2o.glm.wrapper", "h2o.randomForest.wrapper",
             "h2o.gbm.wrapper", "h2o.deeplearning.wrapper")

# Specify Non-Negative Least Square as metalearner
h2o.glm_nn <- function(..., non_negative = TRUE) h2o.glm.wrapper(..., non_negative = non_negative)

# Train the ensemble using 5-fold CV to generate level-one data
fit <- h2o.ensemble(x = x, y = y,
                    training_frame = train,
                    family = family,
                    learner = learner,
                    metalearner = "h2o.glm_nn",
                    cvControl = list(V = 5, shuffle = TRUE))

# Perform 5-fold cross validation repeated 3 times
fit_cv <- h2o.ensemble_cv(fit, training_frame = train, K = 5, times = 3)

# Compute test set performance
perf <- h2o.ensemble_performance_cv(fit_cv, training_frame=train)
perf

# Train with new Metalearner
nfit_cv <- h2o.metalearn_cv(fit_cv, metalearner = "h2o.deeplearning.wrapper")

# Test Performance with new Metalerner
nperf <- h2o.ensemble_performance_cv(nfit_cv, training_frame=train)
nperf

}
