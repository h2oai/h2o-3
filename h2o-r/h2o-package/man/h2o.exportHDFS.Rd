\name{h2o.exportHDFS}
\alias{h2o.exportHDFS}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Export a H2O Model to HDFS
}
\description{
Saves a model built from a H2O algorithm to HDFS.
}
\usage{
h2o.exportHDFS(object, path)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{object}{An \code{\linkS4class{H2OModel}} object representing the model to be exported.}
  \item{path}{The HDFS file path where the model should be saved.}
}
\seealso{
\code{\linkS4class{H2OModel}}
}
\examples{
\dontrun{
# This is an example of how to export H2O models to HDFS.
# The user must modify the path to his or her specific HDFS path for this example to run.
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
irisPath = system.file("extdata", "iris_wheader.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
iris.gbm = h2o.gbm(x = 1:4, y = 5, data = iris.hex)
h2o.exportHDFS(iris.gbm, path = "hdfs://192.168.1.161/datasets/models")
h2o.shutdown(localH2O)
}
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
