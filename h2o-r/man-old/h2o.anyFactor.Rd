\name{h2o.anyFactor}
\alias{h2o.anyFactor}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Determine if an H2O parsed data object contains categorical data. 
}
\description{
Checks if an H2O parsed data object has any columns of categorical data.
}
\usage{
h2o.anyFactor(x)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object.
}
}
\value{
Returns a logical value indicating whether any of the columns in \code{x} are factors.
}
\seealso{
\code{\linkS4class{H2OParsedData}}
}
\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris_wheader.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
h2o.anyFactor(iris.hex)
}
