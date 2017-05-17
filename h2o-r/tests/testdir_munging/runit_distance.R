setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.dist <- function() {
  iris_h2o <- as.h2o(iris)

  references = iris_h2o[11:150,1:4]
  queries    = iris_h2o[1:10,  1:4]

  D = h2o.distance(references, queries, "l1")
  expect_true(min(D) >= 0)
  D = h2o.distance(references, queries, "l2")
  expect_true(min(D) >= 0)
  D = h2o.distance(references, queries, "cosine")
  expect_true(min(D) >= -1)
  expect_true(max(D) <= 1)
  D = h2o.distance(references, queries, "cosine_sq")
  expect_true(min(D) >= 0)
  expect_true(max(D) <= 1)

  A = h2o.distance(references, queries, "l1")
  B = h2o.distance(references, queries, "cosine")
  a = h2o.distance(queries, references, "l1")
  b = h2o.distance(queries, references, "cosine")

  expect_true(all(t(A) == a))
  expect_true(all(t(B) == b))
}

doTest("Test out the distance() functionality", test.dist)
