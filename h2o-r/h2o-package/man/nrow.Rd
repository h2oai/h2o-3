\name{nrow}
\alias{nrow}
\alias{ncol}
\title{
The Number of Rows/Columns of a H2O Dataset
}
\description{
Returns a count of the number of rows in an \code{\linkS4class{H2OParsedData}} object.
}
\usage{
nrow(x)
ncol(x)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object.
  }
}
\value{An integer of length 1 indicating the number of rows or columns in the dataset.
}

\seealso{
\code{\link{dim}} which returns all dimensions
}
\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
nrow(iris.hex)
ncol(iris.hex)
h2o.shutdown(localH2O)
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
