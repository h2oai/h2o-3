\name{h2o.performance_cv}
\alias{h2o.performance_cv}
\title{
Calculate performance metrics for an 'h2o.ensemble_cv' object using the data partitions reserved for testing in each CV fold.
}
\description{
Obtains performance metrics on the holdout data within each fold from a \code{\link[h2oEnsemble]{h2o.ensemble_cv}} fit. Performance metrics from the individual folds are combined using a weighted average for an overall performance metric. 
}
\usage{
h2o.performance_cv(object, training_frame = train, score_base_models = TRUE)
}
\arguments{
  \item{object}{
An object of class 'h2o.ensemble_cv', which is returned from the \code{\link[h2oEnsemble]{h2o.ensemble_cv}} function.
}
  \item{newdata}{
 An H2OFrame object in which to look for variables with which to predict.
}
\item{score_base_models}{
 Logical, defaults to \code{TRUE}.  If set to \code{TRUE}, the base models of the ensemble will also be scored.
}
}

\value{
  \item{h2o.performance_cv object}{
A list of length \code{K*times}.  Each element contains two \linkS4class{H2OModelMetrics} objects (one associated with the base learners, one associated with the overall ensemble) for the corresponding fold/repeat.
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
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.metalearn_cv}}
}
\examples{
# See h2o.ensemble_cv documentation for an example.

}
