\name{h2o.importHDFS}
\alias{h2o.importHDFS}
\alias{h2o.importHDFS.VA}
\alias{h2o.importHDFS.FV}
\title{
  Import from HDFS
}
\description{
  Imports a HDFS file or set of files in a directory and parses them, returning a object containing the identifying hex key.
}
\usage{
## Default method:
h2o.importHDFS(object, path, pattern = "", key = "", parse = TRUE, header, 
  sep = "", col.names, version = 2)

## Import to a ValueArray object:
h2o.importHDFS.VA(object, path, pattern = "", key = "", parse = TRUE, header, 
  sep = "", col.names)

## Import to a FluidVecs object:
h2o.importHDFS.FV(object, path, pattern = "", key = "", parse = TRUE, header, 
  sep = "", col.names)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{object}{
    An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.
  }
  \item{path}{
    The path of the file or folder directory to be imported. If it does not contain an absolute path, the file name is relative to the current working directory.
  }
  \item{pattern}{
  (Optional) Character string containing a regular expression to match file(s) in the folder.
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
(Optional) A \code{\linkS4class{H2OParsedDataVA}} (\code{version = 1}) or \code{\linkS4class{H2OParsedData}} (\code{version = 2}) object containing a single delimited line with the column names for the file.
  }
  \item{version}{
  (Optional) If \code{version = 1}, the file will be imported to a ValueArray object. Otherwise, if \code{version = 2}, the file will be imported as a FluidVecs object.
  }
}

\details{
Calling the method with \code{version = 1} is equivalent to \code{h2o.importHDFS.VA}, and \code{version = 2} is equivalent to \code{h2o.importHDFS.FV}.

When \code{path} is a directory, this method acts like \code{\link{h2o.importFolder}} and concatenates all data files in the folder into a single ValueArray object.

WARNING: In H2O, import is lazy! Do not modify the data files on hard disk until after parsing is complete.
}

\value{
If \code{parse = TRUE}, the function returns an object of class \code{\linkS4class{H2OParsedDataVA}} when \code{version = 1} and an object of class \code{\linkS4class{H2OParsedData}} when \code{version = 2}. Otherwise, when \code{parse = FALSE}, it returns an object of class \code{\linkS4class{H2ORawDataVA}} when \code{version = 1} and an object of class \code{\linkS4class{H2ORawData}} when \code{version = 2}.
}

\seealso{
\code{\link{h2o.importFile}, \link{h2o.importFolder}, \link{h2o.importURL}, \link{h2o.uploadFile}}
}

\examples{
\dontrun{
# This is an example of how to import files from HDFS.
# The user must modify the path to his or her specific HDFS path for this example to run.
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
iris.hex = h2o.importHDFS(localH2O, path = paste("hdfs://192.168.1.161", 
  "datasets/runit/iris_wheader.csv", sep = "/"), parse = TRUE)
class(iris.hex)
summary(iris.hex)
iris.fv = h2o.importHDFS(localH2O, path = paste("hdfs://192.168.1.161", 
  "datasets/runit/iris_wheader.csv", sep = "/"), parse = TRUE, version = 2)
class(iris.fv)

iris_folder.hex = h2o.importHDFS(localH2O, path = paste("hdfs://192.168.1.161", 
  "datasets/runit/iris_test_train", sep = "/"))
summary(iris_folder.hex)
h2o.shutdown(localH2O)
}
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
