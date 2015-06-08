setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

print_diff <- function(r, h2o) {
  if (!isTRUE(all.equal(r,h2o))) {
    Log.info (paste("R :", r))
    Log.info (paste("H2O :" , h2o))
  }
}

#
# This test creates some dates on the server, copies the results
# locally.  It then changes the timezone reloads the original
# data, changes them to dates, and gets a loal copies.
# The two local copies are then checked for the correct time
# offset.
#
test.rdoc_settimezone.golden <- function(H2Oserver) {
  origTZ = h2o.getTimezone(H2Oserver)
  h2o.setTimezone("Etc/UTC", H2Oserver)
  rdf = data.frame(c("Fri Jan 10 00:00:00 1969", "Tue Jan 10 04:00:00 2068", "Mon Dec 30 01:00:00 2002", "Wed Jan 1 12:00:00 2003"))
  colnames(rdf) <- c("c1")
  hdf = as.h2o(H2Oserver, rdf, "hdf")
  hdf$c1 <- as.Date(hdf$c1, "%c")
  ldfUTC <- as.data.frame(hdf)

  h2o.rm(hdf)
  h2o.setTimezone("America/Los_Angeles", H2Oserver)
  hdf = as.h2o(H2Oserver, rdf, "hdf")
  hdf$c1 <- as.Date(hdf$c1, "%c")
  ldfPST <- as.data.frame(hdf)

  diff = ldfUTC - ldfPST
  act = rep(-28800000, 4)

  print_diff(act, diff[,1])
  expect_that(act, equals(diff[,1]))
  
  # erase side effect of test
  h2o.setTimezone(as.character(origTZ[1,1]), H2Oserver)
  testEnd()
}

doTest("R Doc setTimezone", test.rdoc_settimezone.golden)
