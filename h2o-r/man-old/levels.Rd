\name{levels}
\alias{levels}
\title{
Levels of Categorical Data 
}
\description{
Returns a list of the unique values found in a column of categorical data. 
}
\usage{
levels(x)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{x}{Column of categorical data in an \code{\linkS4class{H2OParsedData}} object.}
}

\value{
Returns a list containing one entry for each unique value found in the column of categorical data. 
}

\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
levels(iris.hex[,5])
}
