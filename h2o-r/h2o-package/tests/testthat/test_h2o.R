library(h2o)
library(testthat)

context("H2OConnection")

logFile = "/tmp/h2o_rest.log"

prologue <- function() {
  h2o.startLogging(file = logFile)
}

test_that(".h2o.calcBaseURL works", {
  if(.skip_if_not_developer()) return("Skipped")

  prologue()

  h = new("H2OConnection", ip="www.omegahat.org", port=80)

  url = .h2o.calcBaseURL(conn = h, urlSuffix = "")
  expect_equal(url, "http://www.omegahat.org:80/")

  url = .h2o.calcBaseURL(conn = h, urlSuffix = "foo/bar")
  expect_equal(url, "http://www.omegahat.org:80/foo/bar")

  url = .h2o.calcBaseURL(conn = h, h2oRestApiVersion = 25, urlSuffix = "foo/bar")
  expect_equal(url, "http://www.omegahat.org:80/25/foo/bar")
})

test_that("doRawGET works", {
  if(.skip_if_not_developer()) return("Skipped")
  prologue()

  h = new("H2OConnection", ip="www.omegahat.org", port=80)
  rv = .h2o.doRawGET(conn = h, urlSuffix = "")
  expect_equal(rv$url, "http://www.omegahat.org:80/")
  expect_equal(rv$curlError, FALSE)
  expect_equal(rv$httpStatusCode, 200)
  expect_equal(nchar(rv$payload) >= 500, TRUE)

  h = new("H2OConnection", ip="www.omegahat.org", port=80)
  parms = list(arg1="hi", arg2="there")
  rv = .h2o.doRawGET(conn = h, urlSuffix = "", parms = parms)
  expect_equal(rv$url, "http://www.omegahat.org:80/?arg1=hi&arg2=there")
  expect_equal(rv$curlError, FALSE)
  expect_equal(rv$httpStatusCode, 200)
  expect_equal(nchar(rv$payload) >= 500, TRUE)

  h = new("H2OConnection", ip="www.doesnotexistblahblah.org", port=80)
  rv = .h2o.doRawGET(conn = h, urlSuffix = "")
  expect_equal(rv$curlError, TRUE)
  expect_equal(rv$curlErrorMessage, "Could not resolve host: www.doesnotexistblahblah.org")
})

test_that("doGET works", {
  if(.skip_if_not_developer()) return("Skipped")
  prologue()

  h = new("H2OConnection", ip="www.omegahat.org", port=80)
  rv = .h2o.doGET(conn = h, urlSuffix = "")
  expect_equal(rv$curlError, FALSE)
  expect_equal(rv$curlErrorMessage, "")
  expect_equal(rv$httpStatusCode, 404)
})

test_that("doSafeGET works", {
  if(.skip_if_not_developer()) return("Skipped")
  prologue()

  h = h2o.init()
  payload = .h2o.doSafeGET(conn = h, urlSuffix = "Cloud")
  expect_equal(nchar(payload) >= 500, TRUE)

  ttFailed = FALSE
  tryCatch(.h2o.doSafeGET(conn = h, urlSuffix = "DoesNotExist"),
           error = function(x) { ttFailed <<- TRUE })
  expect_equal(ttFailed, TRUE)
  h2o.shutdown(prompt = FALSE)
})

test_that("doSafePOST works", {
  if(.skip_if_not_developer()) return("Skipped")
  prologue()

  h = h2o.init()
  payload = .h2o.doSafePOST(conn = h, urlSuffix = "LogAndEcho")
  expect_equal(nchar(payload) >= 50, TRUE)

  parms = list(message="hi_there")
  rv = .h2o.doRawPOST(conn = h, h2oRestApiVersion = .h2o.__REST_API_VERSION, urlSuffix = "LogAndEcho", parms = parms)
  expect_equal(rv$url, "http://127.0.0.1:54321/3/LogAndEcho")
  expect_equal(rv$postBody, "message=hi_there")
  expect_equal(rv$curlError, FALSE)
  expect_equal(rv$httpStatusCode, 200)
  expect_equal(nchar(rv$payload) >= 100, TRUE)
  h2o.shutdown(prompt = FALSE)
})

doUploadFileTests <- function(h) {
  irisPath <- system.file("extdata", "iris.csv", package="h2o")
  df = h2o.uploadFile(irisPath)
  expect_equal(nrow(df), 150)
  expect_equal(ncol(df), 5)

  ttFailed = FALSE
  tryCatch({
    invisible(h2o.uploadFile("/file/does/not/exist.csv"))
  }, warning = function(x) {
    ttFailed <<- TRUE
  }, error = function(x) {
    ttFailed <<- TRUE
  })
  expect_equal(ttFailed, TRUE)
}

test_that("H2O can start", {
  if(.skip_if_not_developer()) return("Skipped")
  prologue()

  h = h2o.init()
  doUploadFileTests(h)
  h2o.shutdown(prompt = FALSE)
})

test_that("Report that all tests finished running", {
  if(.skip_if_not_developer()) return("Skipped")

  cat("\n\nAll tests finished running.\n")
})
