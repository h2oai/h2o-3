library(h2o)
library(testthat)

context("H2OConnection")

logFile = "/tmp/h2o_rest.log"

prologue <- function() {
  h2o.startLogging(file = logFile)
}

test_that(".h2o.calcBaseURL works", {
  .skip_if_not_developer()
  prologue()
  
  h = new("h2o.client", ip="www.omegahat.org", port=80)
  
  url = .h2o.calcBaseURL(conn = h, urlSuffix = "")
  expect_equal(url, "http://www.omegahat.org:80/")  
  
  url = .h2o.calcBaseURL(conn = h, urlSuffix = "foo/bar")
  expect_equal(url, "http://www.omegahat.org:80/foo/bar")  
  
  url = .h2o.calcBaseURL(conn = h, h2oRestApiVersion = 25, urlSuffix = "foo/bar")
  expect_equal(url, "http://www.omegahat.org:80/25/foo/bar")    
})

test_that("doRawGET works", {
  .skip_if_not_developer()
  prologue()
  
  h = new("h2o.client", ip="www.omegahat.org", port=80)
  rv = h2o.doRawGET(conn = h, urlSuffix = "")
  expect_equal(rv$url, "http://www.omegahat.org:80/")
  expect_equal(rv$curlError, FALSE)
  expect_equal(rv$httpStatusCode, 200)
  expect_equal(nchar(rv$payload) >= 500, TRUE)

  h = new("h2o.client", ip="www.omegahat.org", port=80)
  parms = list(arg1="hi", arg2="there")
  rv = h2o.doRawGET(conn = h, urlSuffix = "", parms = parms)
  expect_equal(rv$url, "http://www.omegahat.org:80/?arg1=hi&arg2=there")
  expect_equal(rv$curlError, FALSE)
  expect_equal(rv$httpStatusCode, 200)
  expect_equal(nchar(rv$payload) >= 500, TRUE)
  
  h = new("h2o.client", ip="www.doesnotexistblahblah.org", port=80)
  rv = h2o.doRawGET(conn = h, urlSuffix = "")
  expect_equal(rv$curlError, TRUE)
  expect_equal(rv$curlErrorMessage, "Could not resolve host: www.doesnotexistblahblah.org")
})

test_that("doGET works", {
  .skip_if_not_developer()
  prologue()
  
  h = new("h2o.client", ip="www.omegahat.org", port=80)
  rv = h2o.doGET(conn = h, urlSuffix = "")
  expect_equal(rv$curlError, FALSE)
  expect_equal(rv$curlErrorMessage, "")
  expect_equal(rv$httpStatusCode, 404)
})

test_that("doSafeGET works", {
  .skip_if_not_developer()
  prologue()
  
  h = new("h2o.client", ip="www.omegahat.org", port=80)
  payload = h2o.doSafeGET(conn = h, h2oRestApiVersion = -1, urlSuffix = "")
  expect_equal(nchar(payload) >= 500, TRUE)

  ttFailed = FALSE
  tryCatch(h2o.doSafeGET(conn = h, urlSuffix = ""),
           error = function(x) { ttFailed <<- TRUE })
  expect_equal(ttFailed, TRUE)
})

test_that("doSafePOST works", {
  .skip_if_not_developer()
  prologue()

  h = new("h2o.client", ip="www.omegahat.org", port=80)
  payload = h2o.doSafePOST(conn = h, h2oRestApiVersion = -1, urlSuffix = "")
  expect_equal(nchar(payload) >= 500, TRUE)

  parms = list(arg1="hi", arg2="there")
  rv = h2o.doRawPOST(conn = h, urlSuffix = "", parms = parms)
  expect_equal(rv$url, "http://www.omegahat.org:80/")
  expect_equal(rv$postBody, "arg1=hi&arg2=there")
  expect_equal(rv$curlError, FALSE)
  expect_equal(rv$httpStatusCode, 200)
  expect_equal(nchar(rv$payload) >= 500, TRUE)
})

test_that("H2O can start", {
  .skip_if_not_developer()
  h = h2o.init()
  h2o.shutdown(h, prompt = FALSE)
})
