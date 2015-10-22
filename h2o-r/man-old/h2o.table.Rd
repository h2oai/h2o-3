\name{h2o.table}
\alias{h2o.table}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Cross Tabulation of H2O Data
}
\description{
Uses the cross-classifying factors to build a table of counts at each combination of factor levels.
}
\usage{
h2o.table(x, return.in.R = FALSE)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with at most two integer or factor columns.}
  \item{return.in.R}{A logical value indicating whether the result should be converted into an R table.}
}
\value{
If \code{return.in.R = FALSE}, a \code{\linkS4class{H2OParsedData}} object containing the contingency table. This will just be the counts of each factor level when \code{x} has a single column. If \code{return.in.R = TRUE}, the H2O result will be pulled into R and converted into a table object.
}
\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")
summary(prostate.hex)

# Counts of the ages of all patients
head(h2o.table(prostate.hex[,3]))
h2o.table(prostate.hex[,3], return.in.R = TRUE)

# Two-way table of ages (rows) and race (cols) of all patients
head(h2o.table(prostate.hex[,c(3,4)]))
h2o.table(prostate.hex[,c(3,4)], return.in.R = TRUE)
}
