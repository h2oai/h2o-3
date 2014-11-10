\name{h2o.uploadFile}
\alias{h2o.uploadFile}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Upload Local Data File
}
\description{
Uploads a file from the local drive and parses it, returning an object containing the identifying hex key.
}
\usage{
h2o.uploadFile(object, path, key = "", parse = TRUE, header, 
  sep = "", col.names, silent = TRUE, parser_type="AUTO")
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{object}{
An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.
}
  \item{path}{
The complete URL or normalized file path of the file to be imported. Each row of data appears as one line of the file.
}
  \item{key}{
(Optional) The unique hex key assigned to the imported file. If none is given, a key will automatically be generated based on the URL path.
}
  \item{parse}{
(Optional) A logical value indicating whether the file should be parsed after import.
}
  \item{header}{
(Optional) A logical value indicating whether the first line of the file contains column headers. If left empty, the parser will try to automatically detect this.
  }
  \item{sep}{
(Optional) The field separator character. Values on each line of the file are separated by this character. If \code{sep = ""}, the parser will automatically detect the separator.
}
  \item{col.names}{
(Optional) A \code{\linkS4class{H2OParsedData}} object containing a single delimited line with the column names for the file.
  }
  \item{silent}{
(Optional) A logical value indicating whether or not to display an upload progress bar.
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
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\link{h2o.importFile}, \link{h2o.importFolder}, \link{h2o.importHDFS}, \link{h2o.importURL}}
}
\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package = "h2o")
prostate.hex = h2o.uploadFile(localH2O, path = prosPath, key = "prostate.hex")
class(prostate.hex)
summary(prostate.hex)
}
