\name{h2o.prcomp}
\alias{h2o.prcomp}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Principal Components Analysis
}
\description{
Performs principal components analysis on the given data set.
}
\usage{
h2o.prcomp(data, tol = 0, cols = "", max_pc = 5000, key = "", standardize = TRUE, 
retx = FALSE)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{
  An \code{\linkS4class{H2OParsedData}} object on which to run principal components analysis.
  }
  \item{tol}{
  (Optional) A value indicating the magnitude below which components should be omitted. (Components are omitted if their standard deviations are less than or equal to tol times the standard deviation of the first component.) With the default setting \code{tol = 0}, no components are omitted.
  }
  \item{max_pc}{Integer value denoting the number of principle components returned in the output as a R data frame. By default all of the components up to 5000 components will be shown but for much larger number of components it's best to show a subset.}
  \item{cols}{
  (Optional) A vector of column names or indices indicating the features to perform PCA on. By default, all columns in the dataset are analyzed.
  }
    \item{key}{
  (Optional) The unique hex key assigned to the resulting model. If none is given, a key will automatically be generated.
}
  \item{standardize}{
  (Optional) A logical value indicating whether the variables should be shifted to be zero centered and scaled to have unit variance before the analysis takes place.
  }
  \item{retx}{
  (Optional) A logical value indicating whether the rotated variables should be returned.
  }
}
\details{
The calculation is done by a singular value decomposition of the (possibly standardized) data set. 
}
\value{
An object of class \code{\linkS4class{H2OPCAModel}} with slots key, data, and model, where the last is a list of the following components:
\item{standardized }{A logical value indicating whether the data was centered and scaled.}
\item{sdev }{The standard deviations of the principal components (i.e., the square roots of the eigenvalues of the covariance/correlation matrix).}
\item{rotation }{The matrix of variable loadings (i.e., a matrix whose columns contain the eigenvectors).}
}
\note{
The signs of the columns of the rotation matrix are arbitrary, and so may differ between different programs for PCA.
}

%% ~Make other sections like Warning with \section{Warning }{....} ~

\seealso{
%% ~~objects to See Also as \code{\link{help}}, ~~~
\code{\link{h2o.pcr}}
}
\examples{
library(h2o)
localH2O = h2o.init()
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath)
australia.pca = h2o.prcomp(data = australia.hex, standardize = TRUE)
print(australia.pca)
}
