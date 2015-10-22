\name{h2o.downloadCSV}
\alias{h2o.downloadCSV}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Download H2O Data to Disk
}
\description{
Download a H2O dataset to a CSV file on local disk.
}
\usage{
h2o.downloadCSV(data, filename, quiet = FALSE)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData}} object to be downloaded.}
  \item{filename}{A character string indicating the name that the CSV file should be saved to.}
  \item{quiet}{(Optional) If \code{TRUE}, suppress status messages and progress bar.}
}
\details{
WARNING: Files located on the H2O server may be very large! Make sure you have enough hard drive space to accommodate the entire file. 
}
\seealso{
\code{\linkS4class{H2OParsedData}}
}
\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris_wheader.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)

myFile = paste(getwd(), "my_iris_file.csv", sep = .Platform$file.sep)
h2o.downloadCSV(iris.hex, myFile)
file.info(myFile)
file.remove(myFile)
}
