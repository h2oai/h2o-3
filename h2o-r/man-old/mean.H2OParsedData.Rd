\name{mean.H2OParsedData}
\alias{mean.H2OParsedData}
\title{
Arithmetic Mean of H2O Dataset
}
\description{
\code{mean.H2OParsedData}, a method for the \code{\link{mean}} generic. Calculate the mean of each numeric column in a H2O dataset. 
}
\usage{
\method{mean}{H2OParsedData}(x, trim = 0, na.rm = FALSE, ...)
}
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object.}
  \item{trim}{(The fraction (0 to 0.5) of observations to trim from each end of \code{x} before the mean is computed. (Currently unimplemented).}
  \item{na.rm}{Logical value indicating whether \code{NA} or missing values should be stripped before the computation.}
  \item{...}{Potential further arguments. (Currently unimplemented).}
}
\value{
An \code{\linkS4class{H2OParsedData}} object of scalar numeric value representing the arithmetic mean of each numeric column of \code{x}. If \code{x} is not logical or numeric, then \code{NA_real_} is returned, with a warning.
}

\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
mean(prostate.hex$AGE)
}
