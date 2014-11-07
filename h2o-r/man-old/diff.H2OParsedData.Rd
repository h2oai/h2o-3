\name{diff.H2OParsedData}
\alias{diff.H2OParsedData}
\title{
Lagged Differences of H2O Dataset
}
\description{
\code{diff.H2OParsedData}, a method for the \code{\link{diff}} generic. Calculate the lagged and iterated differences of a single numeric column in a H2O dataset. 
}
\usage{
\method{diff}{H2OParsedData}(x, lag = 1, differences = 1, ...)
}
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object.}
  \item{lag}{An integer indicating which lag to use. Must be greater than 0.}
  \item{differences}{An integer indicating the order of the differences. Must be greater than 0.}
  \item{...}{Potential further arguments. (Currently unimplemented).}
}
\value{
An \code{\linkS4class{H2OParsedData}} object with a single numeric column containing the successive lagged and iterated differences. If \code{differences = 1}, this is equivalent to \code{x[(1+lag):n] - x[1:(n-lag)]}. For differences greater than 1, the algorithm is applied recursively to \code{x}.
}

\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
diff(prostate.hex$AGE)
}
