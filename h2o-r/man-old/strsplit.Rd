\name{strsplit}
\alias{strsplit}
\title{Split the Elements of a Character Vector}
\description{ \code{strsplit}, a method for the \code{\link{strsplit}} base method. }
\usage{strsplit(x, split, fixed, perl, useBytes)}
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with a single factor column or an R data frame.}
  \item{split}{A non-empty string. Can be a regular expression.}
  \item{fixed}{ Used by the base method. Ignored by H2OParsedData strsplit. }
  \item{perl}{ Used by the base method. Ignored by H2OParsedData strsplit. }
  \item{useBytes}{ Used by the base method. Ignored by H2OParsedData strsplit. }
  }
\details{
Splits the given factor column on the input split. If split is '', then an error will be thrown. The default is to
split on whitespace.
}
