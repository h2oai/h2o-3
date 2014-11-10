\name{h2o.parseRaw}
\alias{h2o.parseRaw}
\title{
Parse Raw Data File
}
\description{
Parses a raw data file, returning an object containing the identifying hex key.
}
\usage{
h2o.parseRaw(data, key = "", header, sep = "", col.names,parser_type="AUTO")
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{
An \code{\linkS4class{H2ORawData}} object to be parsed.
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
(Optional) A \code{\linkS4class{H2OParsedData}} object containing a single delimited line with the column names for the file.
  }
  \item{parser_type}{
(Optional) Specify the type of data to be parsed. parser_type = "AUTO" is the default, other acceptable values are "SVMLight", "XLS", and "CSV".
  }
}
\details{
After the raw data file is parsed, it will be automatically deleted from the H2O server.
}
\value{
An object of class \code{\linkS4class{H2OParsedData}}, representing the dataset that was parsed.
}
\seealso{
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\link{h2o.importFile}, \link{h2o.importFolder}, \link{h2o.importHDFS}, \link{h2o.importURL}, \link{h2o.uploadFile}}
}
\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.raw = h2o.importFile(localH2O, path = prosPath, parse = FALSE)
# Do not modify prostate.csv on disk at this point!
prostate.hex = h2o.parseRaw(data = prostate.raw, key = "prostate.hex")
# After parsing, it is okay to modify or delete prostate.csv
}
