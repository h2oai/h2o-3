\name{print.h2o.ensemble_performance}
\alias{print.h2o.ensemble_performance}
\title{
  Print method for an 'h2o.ensemble_performance' object
}
\description{
  Print ensemble and base learner performance for an \code{\link[h2oEnsemble]{h2o.ensemble_performance}} object and specific metric.  
}
\usage{
  \method{print}{h2o.ensemble_performance}(x, metric = c("AUTO", "logloss", "MSE", "AUC", "r2"), ...)
}
\arguments{
  \item{x}{
    An object of class 'h2o.ensemble_performance', which is returned from the \code{\link[h2oEnsemble]{h2o.ensemble_performance}} function.
  }
  \item{metric}{
    Metric to use for printed performance. Can be one of "AUTO", "logloss", "MSE", "AUC", "r2".
  }
  \item{...}{
    Additional arguments passed on to the function.
  }
}

\author{
  Erin LeDell \email{erin@h2o.ai}
}

\seealso{
  \code{\link[h2oEnsemble:h2o.ensemble_performance]{h2o.ensemble_performance}}
}

