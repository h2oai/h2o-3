\name{h2o.importFolder}
\alias{h2o.importFolder}
\title{
Import Local Directory of Data Files
}
\description{
  Imports all the files in the local directory and parses them, concatenating the data into a single H2O data matrix and returning an object containing the identifying hex key.
}
\usage{
h2o.importFolder(object, path, pattern = "", key = "", parse = TRUE, header, 
  sep = "", col.names, parser_type="AUTO")
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{object}{
    An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.
  }
  \item{path}{
    The path of the folder directory to be imported. Each row of data appears as one line of the file. If it does not contain an absolute path, the file name is relative to the current working directory.
  }
  \item{key}{
  (Optional) The unique hex key assigned to the imported file. If none is given, a key will automatically be generated based on the file path.
  }
  \item{pattern}{
(Optional) Character string containing a regular expression to match file(s) in the folder.
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
(Optional) An \code{\linkS4class{H2OParsedData}} object containing a single delimited line with the column names for the file.
  }
  \item{parser_type}{
  (Optional) Specify the type of data to be parsed. parser_type = "AUTO" is the default, other acceptable values are "SVMLight", "XLS", and "CSV".
    }
}

\details{
This method imports all the data files in a given folder and concatenates them together row-wise into a single matrix represented by a \code{\linkS4class{H2OParsedData}} object. The data files must all have the same number of columns, and the columns must be lined up in the same order, otherwise an error will be returned.

WARNING: In H2O, import is lazy! Do not modify the data files on hard disk until after parsing is complete.
}

\value{
If \code{parse = TRUE}, the function returns an object of class \code{\linkS4class{H2OParsedData}}. Otherwise, when \code{parse = FALSE}, it returns an object of class  \code{\linkS4class{H2ORawData}}.
}
\seealso{
  %% ~~objects to See Also as \code{\link{help}}, ~~~
  \code{\link{h2o.importFile}, \link{h2o.importHDFS}, \link{h2o.importURL}, \link{h2o.uploadFile}}
}
\examples{
\dontrun{
library(h2o)
localH2O = h2o.init()
myPath = system.file("extdata", "prostate_folder", package = "h2o")
prostate_all.hex = h2o.importFolder(localH2O, path = myPath)
class(prostate_all.hex)
summary(prostate_all.hex)
}
}
