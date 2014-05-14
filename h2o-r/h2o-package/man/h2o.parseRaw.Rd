\name{h2o.parseRaw}
\alias{h2o.parseRaw}
\alias{h2o.parseRaw.VA}
\alias{h2o.parseRaw.FV}
\title{
Parse Raw Data File
}
\description{
Parses a raw data file, returning an object containing the identifying hex key.
}
\usage{
## Default method:
h2o.parseRaw(data, key = "", header, sep = "", col.names, version = 2)

## Import to a ValueArray object:
h2o.parseRaw.VA(data, key = "", header, sep = "", col.names)

## Import to a FluidVecs object:
h2o.parseRaw.FV(data, key = "", header, sep = "", col.names)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{
An \code{\linkS4class{H2ORawDataVA}} (\code{version = 1}) or \code{\linkS4class{H2ORawData}} (\code{version = 2}) object to be parsed.
}
  \item{key}{
(Optional) The hex key assigned to the parsed file.
  }
  \item{header}{
(Optional) A logical value indicating whether the first row is the column header. If missing, H2O will automatically try to detect the presence of a header.
  }
  \item{sep}{
(Optional) The field separator character. Values on each line of the file are separated by this character. If \code{sep = ""}, the parser will automatically detect the separator.
  }
  \item{col.names}{
(Optional) A \code{\linkS4class{H2OParsedDataVA}} (\code{version = 1}) or \code{\linkS4class{H2OParsedData}} (\code{version = 2}) object containing a single delimited line with the column names for the file.
  }
  \item{version}{
  (Optional) If \code{version = 1}, the file will be parsed to a ValueArray object. Otherwise, if \code{version = 2}, the file will be parsed to a FluidVecs object.
  }
}
\details{
Calling the method with \code{version = 1} is equivalent to \code{h2o.parseRaw.VA}, and \code{version = 2} is equivalent to \code{h2o.parseRaw.FV}. \code{h2o.parseRaw.VA} should only be used to parse raw data imported using \code{\link{h2o.importFile.VA}}, \code{\link{h2o.importFolder.VA}}, or one of its variants. Similarly, \code{h2o.parseRaw.FV} should only be used to parse raw data imported using \code{\link{h2o.importFile.FV}}, \code{\link{h2o.importFolder.FV}}, or one of its variants.

After the raw data file is parsed, it will be automatically deleted from the H2O server.
}
\value{
An object of class \code{\linkS4class{H2OParsedDataVA}} (\code{version = 1}) or \code{\linkS4class{H2OParsedData}} (\code{version = 2}), representing the dataset that was parsed.
}
\seealso{
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\link{h2o.importFile}, \link{h2o.importFolder}, \link{h2o.importHDFS}, \link{h2o.importURL}, \link{h2o.uploadFile}}
}
\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.raw = h2o.importFile(localH2O, path = prosPath, parse = FALSE)
# Do not modify prostate.csv on disk at this point!
prostate.hex = h2o.parseRaw(data = prostate.raw, key = "prostate.hex")
# After parsing, it is okay to modify or delete prostate.csv
h2o.shutdown(localH2O)
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
