\name{h2o.getFrame}
\alias{h2o.getFrame}

\title{
Get Reference to H2O Data Set
}
\description{
Get a reference to an existing H2O data set.
}
\usage{
  h2o.getFrame(h2o, key)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{h2o}{An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.}
  \item{key}{A string indicating the unique hex key of the data set to retrieve.}
  }
\value{
Returns an object of class \code{\linkS4class{H2OParsedData}}.
}
\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris.csv", package = "h2o")
h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
h2o.ls(localH2O)
iris.hex = h2o.getFrame(localH2O, "iris.hex")
h2o.shutdown(localH2O)
}
