\name{h2o.nFoldExtractor}
\alias{h2o.nFoldExtractor}

\title{
Extract N-fold holdout splits from H2O Data Set
}
\description{
Split an existing H2O data set into N folds and return a specified holdout split, and the rest.
}
\usage{
  h2o.nFoldExtractor(data, nfolds, fold_to_extract)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData}} object representing the dataset to split.}
  \item{nfolds}{A numeric value indicating the total number of folds created.}
  \item{fold_to_extract}{A numeric value indicating which fold to hold out.}
}
\value{
Returns a list of objects of class \code{\linkS4class{H2OParsedData}}, each corresponding to one of the splits.
}
\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
iris.folds = h2o.nFoldExtractor(iris.hex, nfolds=10, fold_to_extract = 4)
head(iris.folds[[1]])
summary(iris.folds[[1]])
head(iris.folds[[2]])
summary(iris.folds[[2]])
}
