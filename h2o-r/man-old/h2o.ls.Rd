\name{h2o.ls}
\alias{h2o.ls}
\title{Obtain a list of H2O keys from the running instance of H2O}
\description{Allows users to access a list of object keys in the running instance of H2O}
\usage{
h2o.ls(object, pattern)
}
\arguments{
  \item{object}{An \code{H2OClient} object containing the IP address and port number of the H2O server.}
  \item{pattern}{A string indicating the type of key to be returned. When pattern is left is unspecified all keys are returned.}
}

\value{Returns a list of hex keys in the current instance of H2O, and their associated sizes in bytes.}

\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")
s = runif(nrow(prostate.hex))
prostate.train = prostate.hex[s <= 0.8,]
prostate.train = h2o.assign(prostate.train, "prostate.train")
h2o.ls(localH2O)
}

