\name{print.h2o.performance_cv}
\alias{print.h2o.performance_cv}
\title{
  Print method for an 'h2o.performance_cv' object
}
\description{
  Print performance metrics for an \code{\link[h2oEnsemble]{h2o.performance_cv}} object and specific metric. The following metrcs are printed: base learner performance within each fold/repeat, ensemble performance within each fold/repeat, and overall mean ensemble cross-validated performance. 
}
\usage{
  \method{print}{h2o.performance_cv}(x, metric = c("AUTO", "logloss", "MSE", "AUC", "r2"), ...)
}
\arguments{
  \item{x}{
    An object of class 'h2o.performance_cv', which is returned from the \code{\link[h2oEnsemble]{h2o.performance_cv}} function.
  }
  \item{metric}{
    Metric to use for printed performance. Can be one of "AUTO", "logloss", "MSE", "AUC", "r2".
  }
  \item{...}{
    Additional arguments passed on to the function.
  }
}

\author{
Rebecca Z. Krouse, Agustin Calatroni 
}

\seealso{
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.ensemble_cv}},
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.metalearn_cv}},
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.performance_cv}}
}
