\name{hist.H2OParsedData}
\alias{hist.H2OParsedData}
\title{Obtain and display a histogram for H2O parsed data.}
\description{ \code{hist.H2OParsedData}, a method for the \code{\link{hist}} generic. Obtain and
returns a histogram for an \code{\linkS4class{H2OParsedData}} object.}
\usage{\method{hist}{H2OParsedData}(x, freq = TRUE, \dots)}
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with a single numeric column.}
  \item{freq}{logical; if TRUE, the histogram graphic is a representation of frequencies, 
  the counts component of the result; if FALSE the probability density is plotted.}
  \item{\dots}{Additional arguments affecting the summary produced. (Currently unimplemented).}
  }
\details{
Counts of numeric values are plotted in cells which is defined in the histogram object as breaks. The height of a rectangle is proportional to the number of points falling into the cell.
}
\value{
An object of class "histogram" which is a list with components:
\item{breaks}{the n+1 cell boundaries.}
\item{counts}{n integers; count of values for each nth cell.}
\item{density}{the relative frequencies counts/(rows*bin_size) for each cell.}
\item{mids}{the n cell midpoints.}
\item{xname}{a character string with the column name of the vector.}
\item{equidist}{logical, indicating if the distances between breaks are all the same}
}

\examples{
# Request hists for an H2O parsed data set: 
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)

# Request a histogram for a subset of columns in an H2O parsed data set 
hist(prostate.hex[,3])
}
