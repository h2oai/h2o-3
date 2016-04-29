\name{h2o.ensemble_performance}
\alias{h2o.ensemble_performance}
\title{
Calculate performance metrics for an 'h2o.ensemble' object on a new H2OFrame
}
\description{
Obtains performance metrics on a new data set (typically a test set) from a \code{\link[h2oEnsemble]{h2o.ensemble}} fit.  
}
\usage{
h2o.ensemble_performance(object, newdata, score_base_models = TRUE)
}
\arguments{
  \item{object}{
An object of class 'h2o.ensemble', which is returned from the \code{\link[h2oEnsemble]{h2o.ensemble}} function.
}
  \item{newdata}{
 An H2OFrame object in which to look for variables with which to predict.
}
\item{score_base_models}{
 Logical, defaults to \code{TRUE}.  If set to \code{TRUE}, the base models of the ensemble will also be scored.
}
}

\value{
\item{ensemble}{
An \linkS4class{H2OModelMetrics} object providing the ensemble performance metrics on \code{newdata}.
}
\item{base}{
A list of \linkS4class{H2OModelMetrics} objects, one for each base learner in the ensemble, providing the base learner performance metrics on \code{newdata}.
}
}

\author{
Erin LeDell \email{erin@h2o.ai}
}

\seealso{
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.ensemble}}
}
\examples{
# See h2o.ensemble or h2o.stack documentation for an example.

}

