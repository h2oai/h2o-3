
## unit tests for as.h2o generic and methods
# output class
# output dimensions
# h2o frame key on collisions
# tests imported from python from invalid names

library(h2o)
h2o.init()
h2o.no_progress()
hi <- as.h2o(iris)
he <- as.h2o(euro)
hl <- as.h2o(letters)
hm <- as.h2o(state.x77)
hh <- as.h2o(hi)
stopifnot(
  is.h2o(hi), dim(hi)==dim(iris), h2o.getId(hi)=="iris",
  is.h2o(he), dim(he)==c(length(euro),1L), h2o.getId(he)=="euro",
  is.h2o(hl), dim(hl)==c(length(letters),1L), h2o.getId(hl)=="letters",
  is.h2o(hm), dim(hm)==dim(state.x77), h2o.getId(hm)=="state.x77",
  is.h2o(hh), dim(hh)==dim(hi), h2o.getId(hh)=="hi"
)
if (requireNamespace("Matrix", quietly=TRUE)) {
  data <- rep(0, 100)
  data[(1:10)^2] <- 1:10 * pi
  m <- matrix(data, ncol = 20, byrow = TRUE)
  hs1 <- as.h2o(m <- Matrix::Matrix(m, sparse = TRUE))
  hs2 <- as.h2o(m)
  stopifnot(
    is.h2o(hs1),
    dim(hs1)==dim(m),
    # also test proper name substitute
    grepl("^Matrix", h2o.getId(hs1)), # random id with Matrix prefix
    h2o.getId(hs2)=="m"
  )
} else {
  cat("Matrix package not available, tests skipped\n")
}

x <- data.frame(a=1)
h1 <- as.h2o(x)
x <- data.frame(a=2)
h2 <- as.h2o(x)
x <- data.frame(a=3)
h3 <- try(as.h2o(x, destination_frame="x"), silent=TRUE)
h4 <- try(as.h2o(h2, destination_frame="x"), silent=TRUE) # as.h2o.H2OFrame method
stopifnot(
  h2o.getId(h1)=="x",
  h2o.getId(h2)!="x", # this is random now because it is overlapping with existing one
  inherits(h3, "try-error"), # error because user made attempt to override frame
  inherits(h4, "try-error")
)

# tests imported from python for invalid names
df <- data.frame(a=1)
expr <- quote({
  as.h2o(df, "ab;cd")
  as.h2o(df, "one/two/three/four")
  as.h2o(df, "I'm_declaring_a_thumb_war")
  as.h2o(df, "five\\six\\seven\\eight")
  as.h2o(df, "finger guns proliferate")
  as.h2o(df, "9_10_11_12")
  as.h2o(df, "digits|cant|protect|themselves")
  as.h2o(df, "(thirteen,fourteen,fifteen,sixteen)")
  as.h2o(df, "UNSC_cant_intervene?")
})
err <- sapply(as.list(expr)[-1L], function(x) tryCatch(eval(x), error = function(e) e$message))
stopifnot(grepl("^`key` must match", err))

q("no")
