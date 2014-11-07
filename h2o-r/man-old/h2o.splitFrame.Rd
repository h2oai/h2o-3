\name{h2o.splitFrame}
\alias{h2o.splitFrame}

\title{
Split an H2O Data Set
}
\description{
Split an existing H2O data set according to user-specified ratios.
}
\usage{
  h2o.splitFrame(data, ratios = 0.75, shuffle = FALSE)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData}} object representing the dataset to split.}
  \item{ratios}{A numeric value or array indicating the ratio of total rows contained in each split.}
  \item{shuffle}{A logical value indicating whether to shuffle the rows before splitting.}
}
\value{
Returns a list of objects of class \code{\linkS4class{H2OParsedData}}, each corresponding to one of the splits.
}
\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
iris.split = h2o.splitFrame(iris.hex, ratios = c(0.2, 0.5))
head(iris.split[[1]])
summary(iris.split[[1]])
}
