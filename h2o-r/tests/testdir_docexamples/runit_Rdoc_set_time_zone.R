setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



print_diff <- function(r, h2o) {
  if (!isTRUE(all.equal(r,h2o))) {
    h2oTest.logInfo(paste("R :", r))
    h2oTest.logInfo(paste("H2O :" , h2o))
  }
}

#
# This test creates some dates on the server, copies the results
# locally.  It then changes the timezone reloads the original
# data, changes them to dates, and gets a loal copies.
# The two local copies are then checked for the correct time
# offset.
#
test.rdoc_settimezone.golden <- function() {
  origTZ = h2o.getTimezone()
  #test 1
  h2o.setTimezone("Etc/UTC")
  rdf = data.frame(c("Fri Jan 10 00:00:00 1969", "Tue Jan 10 04:00:00 2068", "Mon Dec 30 01:00:00 2002", "Wed Jan 1 12:00:00 2003"))
  colnames(rdf) <- c("c1")
  hdf = as.h2o(rdf, "hdf")
  hdf$c1 <- as.Date(hdf$c1, "%c")
  ldfUTC <- as.data.frame(hdf)

  h2o.rm(hdf)
  h2o.setTimezone("America/Los_Angeles")
  hdf = as.h2o(rdf, "hdf")
  hdf$c1 <- as.Date(hdf$c1, "%c")
  ldfPST <- as.data.frame(hdf)

  diff = ldfUTC - ldfPST
  act = rep(-28800000, 4)

  print_diff(act, diff[,1])
  expect_that(act, equals(diff[,1]))

  #test 2 - make sure returned years/months have the same timezone as interpretation
  h2o.setTimezone("Etc/UTC")
  rdf <- data.frame(dates = c("2014-01-07", "2014-01-30", "2014-01-31", "2014-02-01", "2014-02-02", "2014-10-31", "2014-11-01"), stringsAsFactors = FALSE)
  hdf <- as.h2o(rdf, "hdf")
  hdf$dates <- as.Date(hdf$dates,"%Y-%m-%d")
  hdf$year <- year(hdf$dates)
  hdf$month <- month(hdf$dates)
  hdf$day <- day(hdf$dates)
  hdf$hour <- hour(hdf$dates)
  ldf <- as.data.frame(hdf)
  edf <- data.frame(year = c(114, 114, 114, 114, 114, 114, 114),
                   month = c(1, 1, 1, 2, 2, 10, 11),
                   day = c(7, 30, 31, 1, 2, 31, 1),
                   hour = c(0, 0, 0, 0, 0, 0, 0)) 

  print_diff(edf$year, ldf$year)
  expect_that(edf$year, equals(ldf$year))
  print_diff(edf$month, ldf$month)
  expect_that(edf$month, equals(ldf$month))
  print_diff(edf$day, ldf$day)
  expect_that(edf$day, equals(ldf$day))
  print_diff(edf$hour, ldf$hour)
  expect_that(edf$hour, equals(ldf$hour))

  # erase side effect of test
  h2o.setTimezone(origTZ)
  h2o.rm(hdf)
  
}

h2oTest.doTest("R Doc setTimezone", test.rdoc_settimezone.golden)
