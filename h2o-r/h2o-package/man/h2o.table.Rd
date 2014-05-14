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
h2o.table(x)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with at most two integer or factor columns.}
}
\value{
A \code{\linkS4class{H2OParsedData}} object containing the contingency table. If \code{x} has a single column, this will just be the counts of each factor level.
}
\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath, key = "prostate.hex")
summary(prostate.hex)

# Counts of the ages of all patients
head(h2o.table(prostate.hex[,3]))

# Two-way table of ages (rows) and race (cols) of all patients
head(h2o.table(prostate.hex[,c(3,4)]))
h2o.shutdown(localH2O)
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
