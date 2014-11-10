\name{h2o.importFile}
\alias{h2o.importFile}
\title{
Import Local Data File
}
\description{
Imports a file from the local path and parses it, returning an object containing the identifying hex key.
}
\usage{
h2o.importFile(object, path, key = "", parse = TRUE, header, sep = "", col.names,
               parser_type="AUTO")
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{object}{
An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.
}
  \item{path}{
The path of the file to be imported. Each row of data appears as one line of the file. If it does not contain an absolute path, the file name is relative to the current working directory.
}
  \item{key}{
(Optional) The unique hex key assigned to the imported file. If none is given, a key will automatically be generated based on the file path.
}
  \item{parse}{
(Optional) A logical value indicating whether the file should be parsed after import.
}
  \item{header}{
(Optional) A logical value indicating whether the first row is the column header. If missing, H2O will automatically try to detect the presence of a header.
  }
  \item{sep}{
(Optional) The field separator character. Values on each line of the file are separated by this character. If \code{sep = ""}, the parser will automatically detect the separator.
  }
  \item{col.names}{
(Optional) A \code{\linkS4class{H2OParsedData}} object containing a single delimited line with the column names for the file.
  }
  \item{parser_type}{
(Optional) Specify the type of data to be parsed. parser_type = "AUTO" is the default, other acceptable values are "SVMLight", "XLS", and "CSV".
  }
}
\details{
WARNING: In H2O, import is lazy! Do not modify the data on hard disk until after parsing is complete.
}
\value{
If \code{parse = TRUE}, the function returns an object of class \code{\linkS4class{H2OParsedData}}. Otherwise, when \code{parse = FALSE}, it returns an object of class \code{\linkS4class{H2ORawData}}.
}
\seealso{
\code{\link{h2o.importFolder}, \link{h2o.importHDFS}, \link{h2o.importURL}, \link{h2o.uploadFile}}
}
\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
class(iris.hex)
summary(iris.hex)
}
