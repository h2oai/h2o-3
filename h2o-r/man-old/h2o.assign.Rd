\name{h2o.assign}
\alias{h2o.assign}
\title{Assigns an H2O hex.key to an H2O object so that it can be utilized in subsequent calls}
\description{ Allows users to assign H2O hex.keys to objects in their R environment so that they can manipulate H2O data frames and parsed data objects.}
\usage{h2o.assign(data, key)}
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData} object}}
  \item{key}{The hex key to be associated with the H2O parsed data object}
  }
\value{The function returns an object of class \code{\linkS4class{H2OParsedData}} 
}

\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package = "h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
psa.qs = quantile(prostate.hex$PSA)
PSA.outliers = prostate.hex[prostate.hex$PSA <= psa.qs[2] | prostate.hex$PSA >= psa.qs[10],]
PSA.outliers = h2o.assign(PSA.outliers, "PSA.outliers")
summary(PSA.outliers)
head(prostate.hex)
head(PSA.outliers)
}
