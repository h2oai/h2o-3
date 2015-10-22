\name{h2o.rebalance}
\alias{h2o.rebalance}

\title{
Rebalance a H2O data frame
}
\description{
Rebalance (repartition) an existing H2O data set into given number of chunks (per Vec), for load-balancing across multiple threads or nodes. Does not alter data.
}
\usage{
  h2o.rebalance(data, chunks, key)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData}} object representing the dataset to rebalance.}
  \item{chunks}{A numeric value indicating how many chunks to rebalance the dataset into. Suggested: Around 4 chunks per CPU core.}
  \item{key}{Destination key for rebalanced \code{\linkS4class{H2OParsedData}} object. }
}
\value{
Returns the rebalanced object of class \code{\linkS4class{H2OParsedData}}.
}
\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
iris.reb = h2o.rebalance(iris.hex, chunks = 32)
summary(iris.reb)
iris.reb2 = h2o.rebalance(iris.hex, chunks = 32, key = "iris.rebalanced")
summary(iris.reb2)
}
