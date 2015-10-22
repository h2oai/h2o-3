\name{h2o.getModel}
\alias{h2o.getModel}

\title{
Get Reference to H2O Model
}
\description{
Get a reference to an existing H2O model.
}
\usage{
  h2o.getModel(h2o, key)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{h2o}{An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.}
  \item{key}{A string indicating the unique hex key of the model to retrieve.}
  }
\value{
Returns an object that is a subclass of \code{\linkS4class{H2OModel}}.
}
\examples{
library(h2o)
localH2O = h2o.init()

iris.hex <- as.h2o(localH2O, iris, "iris.hex")
model <- h2o.randomForest(x = 1:4, y = 5, data = iris.hex)
model.retrieved <- h2o.getModel(localH2O, model@key)
h2o.shutdown(localH2O)
}
