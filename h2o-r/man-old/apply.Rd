\name{apply}
\alias{apply}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{Applies a function over an H2O parsed data object.
}
\description{
Applies a function over an H2O parsed data object (an array).
}
\usage{
apply(X, MARGIN, FUN, \dots)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{X}{
  An \code{\linkS4class{H2OParsedData}} object.
}
  \item{MARGIN}{
The margin along wich the function should be applied
}
  \item{FUN}{
The function to be applied by H2O. 
}
  \item{\dots}{
Optional arguments to FUN. (Currently unimplemented).
  }
}
\value{
Produces a new \code{\linkS4class{H2OParsedData}} of the output of the applied function. The output is stored in H2O so that it can be used in subsequent H2O processes. 
}

\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
summary(apply(iris.hex, 1, sum))
}

