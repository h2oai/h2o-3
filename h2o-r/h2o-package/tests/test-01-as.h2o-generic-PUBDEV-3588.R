library(h2o)
h2o.init(startH2O=FALSE)
is.h2o <- function(x) inherits(x, "H2OFrame")
hi <- as.h2o(iris)
he <- as.h2o(euro)
hl <- as.h2o(letters)
hm <- as.h2o(state.x77)
hh <- as.h2o(hi)
stopifnot(is.h2o(hi), dim(hi)==dim(iris),
          is.h2o(he), dim(he)==c(length(euro),1L),
          is.h2o(hl), dim(hl)==c(length(letters),1L),
          is.h2o(hm), dim(hm)==dim(state.x77),
          is.h2o(hh), dim(hh)==dim(hi))
# if (requireNamespace("Matrix", quietly=TRUE)) {
#   data <- rep(0, 100)
#   data[(1:10)^2] <- 1:10 * pi
#   m <- matrix(data, ncol = 20, byrow = TRUE)
#   m <- Matrix::Matrix(m, sparse = TRUE)
#   hs <- as.h2o(m)
#   stopifnot(is.h2o(hs), dim(hs)==dim(m))
# }
