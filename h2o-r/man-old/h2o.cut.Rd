\name{h2o.cut}
\alias{h2o.cut}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Convert H2O Numeric Data to Factor
}
\description{
Divides the range of the H2O data into intervals and codes the values according to which interval they fall in. The leftmost interval corresponds to level one, the next is level two, etc.
}
\usage{
h2o.cut(x, breaks)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with numeric columns.}
  \item{breaks}{A numeric vector of two or more unique cut points.}
}
\value{
A \code{\linkS4class{H2OParsedData}} object containing the factored data with intervals as levels.
}
\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris_wheader.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
summary(iris.hex)

# Cut sepal length column into intervals determined by min/max/quantiles
sepal_len.cut = h2o.cut(iris.hex$sepal_len, c(4.2, 4.8, 5.8, 6, 8))
head(sepal_len.cut)
summary(sepal_len.cut)
}
