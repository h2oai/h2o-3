\name{h2o.ignoreColumns}
\alias{h2o.ignoreColumns}
\title{ Returns columns' names of a parsed H2O data object that are recommended to be ignored in an analysis}
\description{Returns columns' names of a parsed  \code{\linkS4class{H2OParsedData} object if the columns have high counts of NA entries, threshold for high NA count can be set by user, or are columns with only one value.}}
\usage{
h2o.ignoreColumns(data, max_na = 0.2)
}
\arguments{
  \item{data}{ An\code{\linkS4class{H2OParsedData}} object.}
  \item{max_na}{ A numeric between 0 and 1 representing the proportion of NAs in a column.}
}

\value{Returns a vector of column names. 
}
\examples{
\dontrun{
library(h2o)
localH2O = h2o.init()
airlinesURL = "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv"
airlines.hex = h2o.importFile(localH2O, path = airlinesURL, key = "airlines.hex")
h2o.ignoreColumns(airlines.hex)
}
}
