\name{h2o.uploadFile}
\alias{h2o.uploadFile}
\alias{h2o.uploadFile.VA}
\alias{h2o.uploadFile.FV}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Upload Local Data File
}
\description{
Uploads a file from the local drive and parses it, returning an object containing the identifying hex key.
}
\usage{
## Default method:
h2o.uploadFile(object, path, key = "", parse = TRUE, header, 
  sep = "", col.names, silent = TRUE, version = 2)

## Import to a ValueArray object:
h2o.uploadFile.VA(object, path, key = "", parse = TRUE, header, 
  sep = "", col.names, silent = TRUE)

## Import to a FluidVecs object:
h2o.uploadFile.FV(object, path, key = "", parse = TRUE, header, 
  sep = "", col.names, silent = TRUE)
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
(Optional) A \code{\linkS4class{H2OParsedDataVA}} (\code{version = 1}) or \code{\linkS4class{H2OParsedData}} (\code{version = 2}) object containing a single delimited line with the column names for the file.
  }
  \item{silent}{
(Optional) A logical value indicating whether or not to display an upload progress bar.
}
\item{version}{
  (Optional) If \code{version = 1}, the file will be imported to a ValueArray object. Otherwise, if \code{version = 2}, the file will be imported as a FluidVecs object.
  }
}

\details{
Calling the method with \code{version = 1} is equivalent to \code{h2o.uploadFile.VA}, and \code{version = 2} is equivalent to \code{h2o.uploadFile.FV}.

WARNING: In H2O, import is lazy! Do not modify the data on hard disk until after parsing is complete.
}

\value{
If \code{parse = TRUE}, the function returns an object of class \code{\linkS4class{H2OParsedDataVA}} when \code{version = 1} and an object of class \code{\linkS4class{H2OParsedData}} when \code{version = 2}. Otherwise, when \code{parse = FALSE}, it returns an object of class \code{\linkS4class{H2OParsedDataVA}} when \code{version = 1} and an object of class \code{\linkS4class{H2ORawData}} when \code{version = 2}.
}
\seealso{
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\link{h2o.importFile}, \link{h2o.importFolder}, \link{h2o.importHDFS}, \link{h2o.importURL}}
}
\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
prosPath = system.file("extdata", "prostate.csv", package = "h2o")
prostate.hex = h2o.uploadFile(localH2O, path = prosPath, key = "prostate.hex")
class(prostate.hex)
summary(prostate.hex)
prostate.fv = h2o.uploadFile(localH2O, path = prosPath, key = "prostate.fv", version = 2)
class(prostate.fv)
h2o.shutdown(localH2O)
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
