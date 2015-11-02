\name{predict.h2o.ensemble}
\alias{predict.h2o.ensemble}
\title{
Predict method for an 'h2o.ensemble' object
}
\description{
Obtains predictions on a new data set from a \code{\link[h2oEnsemble]{h2o.ensemble}} fit.  
}
\usage{
\method{predict}{h2o.ensemble}(object, newdata, ...)
}
\arguments{
  \item{object}{
An object of class 'h2o.ensemble', which is returned from the \code{\link[h2oEnsemble]{h2o.ensemble}} function.
}
  \item{newdata}{
 An H2O Frame object in which to look for variables with which to predict.
}
\item{...}{
 Additional arguments passed on to the function.
}
}

\value{
\item{pred}{
A vector of predicted values from ensemble fit.
}
\item{basepred}{
An H2O Frame object with the predicted values from each base learner algorithm for the rows in \code{newdata}.  
}
}

\author{
Erin LeDell \email{erin@h2o.ai}
}

\seealso{
\code{\link[h2oEnsemble:h2o.ensemble]{h2o.ensemble}}
}
\examples{
# See h2o.ensemble documentation for an example.

}

