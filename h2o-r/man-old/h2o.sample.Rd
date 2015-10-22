\name{h2o.sample}
\alias{h2o.sample}

\title{
Sample an H2O Data Set
}
\description{
Sample an existing H2O Frame by number of observations.
}
\usage{
  h2o.sample(data, nobs, seed)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData}} object representing the dataset to sample.}
  \item{nobs}{The number of observations to be in the resulting frame.}
  \item{seed}{A seed for repeatable sampling.}
}
\value{
Returns an \code{\linkS4class{H2OParsedData}} containing `nobs` number of rows, sampled at random.
}
\examples{
library(h2o)
localH2O = h2o.init()
hex <- as.h2o(localH2O, iris)

# sample the data
a <- h2o.sample(hex, nobs = 99)

# check that the number of rows is 99
dim(a)
}
