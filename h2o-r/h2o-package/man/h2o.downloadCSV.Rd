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
h2o.downloadCSV(data, filename)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData}} object to be downloaded.}
  \item{filename}{A character string indicating the name that the CSV file should be saved to.}
}
\details{
This method requires wget or curl to be installed on your local system. WARNING: Files located on the H2O server may be very large! Make sure you have enough hard drive space to accommodate the entire file. 
}
\seealso{
\code{\linkS4class{H2OParsedData}, \linkS4class{H2OParsedDataVA}}
}
\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
irisPath = system.file("extdata", "iris_wheader.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)

myFile = paste(getwd(), "my_iris_file.csv", sep = .Platform$file.sep)
h2o.downloadCSV(iris.hex, myFile)
file.info(myFile)
file.remove(myFile)
h2o.shutdown(localH2O)
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
