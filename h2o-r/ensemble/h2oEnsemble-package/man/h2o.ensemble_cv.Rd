\name{h2o.ensemble_cv}
\alias{h2o.ensemble_cv}

\title{
H2O Ensemble Cross Validation
}
\description{
This function performs repeated K-fold cross validation of the H2O Ensemble object specified by the user.
}
\usage{
h2o.ensemble_cv(model, training_frame = train, 
                K = 3, times = 2, 
                seed = 1)
}

\arguments{
  \item{model}{
An object of class h2o.ensemble.
}

  \item{training_frame}{
An H2OFrame object containing the variables in the model.
}

  \item{K}{
An integer representing the number of CV folds.  Defaults to 3.
}

  \item{times}{
An integer representing the number of CV repeats. Defaults to 2. Use \code{times=1} for non-repeated K-fold cross validation.
}

  \item{seed}{
A random seed to be set (integer); defaults to 1. If \code{NULL}, then a random seed will not be set.  The seed is set prior to creating the repeated CV folds.
}
}

\value{
  \item{h2o.ensemble_cv object}{
A list of length \code{K*times}.  Each element contains the h2o.ensemble output for the corresponding fold/repeat. Indices of the training set are stored as \code{tt_ind}. 
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
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.ensemble}},
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.metalearn_cv}},
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



