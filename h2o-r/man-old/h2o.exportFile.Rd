\name{h2o.exportFile}
\alias{h2o.exportFile}
\title{
Export H2O Data Frame to a File.
}

\description{
Export an H2O Data Frame (which can be either VA or FV) to a file.  This file may be on the H2O instance's local filesystem, or to HDFS (preface the path with hdfs://) or to S3N (preface the path with s3n://).
}

\usage{
## Default method:
h2o.exportFile(data, path, force = FALSE)
}

\arguments{
  \item{data}{
An \code{\linkS4class{H2OParsedData}} data frame.
}
  \item{path}{
The path to write the file to.  Must include the directory and filename.  May be prefaced with hdfs:// or s3n://.  Each row of data appears as one line of the file.
}
  \item{force}{
  (Optional) If \code{force = TRUE} any existing file will be overwritten. Otherwise if the file already exists the operation will fail.
  }
}

\value{
None.  (The function will stop if it fails.)
}

\examples{
\dontrun{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
h2o.exportFile(iris.hex, path = "/path/on/h2o/server/filesystem/iris.csv")
h2o.exportFile(iris.hex, path = "hdfs://path/in/hdfs/iris.csv")
h2o.exportFile(iris.hex, path = "s3n://path/in/s3/iris.csv")
}
}
