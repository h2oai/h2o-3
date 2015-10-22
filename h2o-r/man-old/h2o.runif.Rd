\name{h2o.runif}
\alias{h2o.runif}
\title{Produces a vector of specified length contain random uniform numbers}
\description{Produces a vector of random uniform numbers.}
\usage{h2o.runif(x, min = 0, max = 1, seed = -1)}
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with number of rows equal to the number of elements the vector of random numbers should have.}
  \item{min}{An integer specifying the lower bound of the distribution.}
  \item{max}{An integer specifying the upper bound of the distribution.}
  \item{seed}{(Optional) Random seed used to generate draws from the uniform distribution. The default of -1 results in a seed equal to the current system time in milliseconds.}
  }
\details{
\code{x} must be a \code{\linkS4class{H2OParsedData}} object so that H2O can generate random numbers aligned with the dataset for efficient large-scale sampling and filtering.
}
\value{A vector of random, uniformly distributed numbers. The elements are between 0 and 1 unless otherwise specified.}
\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")
s = h2o.runif(prostate.hex)
summary(s)

prostate.train = prostate.hex[s <= 0.8,]
prostate.train = h2o.assign(prostate.train, "prostate.train")
prostate.test = prostate.hex[s > 0.8,]
prostate.test = h2o.assign(prostate.test, "prostate.test")
nrow(prostate.train) + nrow(prostate.test)
}
