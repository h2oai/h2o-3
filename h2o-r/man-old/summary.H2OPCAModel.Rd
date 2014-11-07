\name{summary.H2OPCAModel}
\alias{summary.H2OPCAModel}
\title{ Summarizes the H2O PCA Model }
\description{
\code{summary.H2OPCAModel}, a method for the \code{\link{summary}} generic. Summarizes the importance of each principal component returned by \code{\link{h2o.prcomp}}.
}
\usage{\method{summary}{H2OPCAModel}(object, ...)}
\arguments{
  \item{object}{An \code{\linkS4class{H2OPCAModel}} object.}
  \item{...}{Additional arguments affecting the summary produced. (Currently unimplemented).}
}
\value{
A matrix displaying the standard deviation, proportion of variance explained and cumulative proportion of variance explained by each principal component.
}
\examples{
library(h2o)
localH2O = h2o.init()
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath)
australia.pca = h2o.prcomp(data = australia.hex, standardize = TRUE)
summary(australia.pca)
}
