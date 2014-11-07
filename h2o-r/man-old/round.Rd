\name{Round}
\alias{round.H2OParsedData}
\alias{signif.H2OParsedData}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Rounding of Numbers
}
\description{
\code{round} rounds the values in a \code{\linkS4class{H2OParsedData}} object to the specified number of decimal places.

\code{signif} rounds the values in a \code{\linkS4class{H2OParsedData}} object to the specified number of significant digits.
}
\usage{
\method{round}{H2OParsedData}(x, digits = 0)
\method{signif}{H2OParsedData}(x, digits = 6)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with numeric entries.}
  \item{digits}{Single number specifying decimal places (\code{round}) or significant digits (\code{signif}) to use. Negative values are interpreted as a power of ten, e.g. \code{round(x, digits = -2)} round to the nearest hundred.}
}

\details{
This method uses the IEC 60559 standard for rounding to the even digit, so 0.5 goes to 0 and -1.5 goes to -2. See the Java documentation of \code{RoundingMode.HALF_EVEN} for more details and examples.
}

\value{
Returns a \code{\linkS4class{H2OParsedData}} object with each entry rounded as specified. An error will occur if any of these entries is non-numeric.
}

\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
iris.data = iris.hex[,1:3]

iris.rounded = round(iris.data)
head(iris.rounded)
iris.signif = signif(iris.data, 2)
head(iris.signif)
}
