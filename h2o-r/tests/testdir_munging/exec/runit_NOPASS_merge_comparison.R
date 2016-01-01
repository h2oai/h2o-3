setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.merge_comparison <- function() {
  h2oTest.logInfo("Verify accuracy of merge")

  left <- data.frame(fruit = c('apple', 'orange', 'banana', 'lemon', 'strawberry', 'blueberry'),
    color = c('red', 'orange', 'yellow', 'yellow', 'red', 'blue'))
  right <- data.frame(fruit = c('apple', 'orange', 'banana', 'lemon', 'strawberry', 'watermelon'),
    citrus = c(F, T, F, T, F, F))

  h2oTest.logInfo("Change datasets into H2O H2OFrames")
  l.hex <- as.h2o(left)
  r.hex <- as.h2o(right)


  # H2O doesn't sort
  h2oTest.logInfo("Default parameters")
  dflt.hex <- h2o.merge(l.hex, r.hex)
  dflt.r <- merge(left, right)
  dflt.h2o <- as.data.frame(dflt.hex)

  # R sorts during merge, we need to sort to compare
  dflt.sorted <- dflt.h2o[order(dflt.h2o$fruit),]
  row.names(dflt.sorted) <- 1:6
  expect_equal(dflt.sorted, dflt.r)

  h2oTest.logInfo("Left Outer")
  left.hex <- h2o.merge(l.hex, r.hex, T, F)
  left.r <- merge(left, right, all.x = T, all.y = F)
  left.h2o <- as.data.frame(left.hex)

  # R sorts during merge, we need to sort to compare
  left.sorted <- left.h2o[order(left.h2o$fruit),]
  row.names(left.sorted) <- 1:6
  expect_equal(left.sorted, left.r)

  h2oTest.logInfo("Right Outer")
  rite.hex <- h2o.merge(l.hex, r.hex, F, T)
  rite.r <- merge(left, right, all.x = F, all.y = T)
  rite.h2o <- as.data.frame(rite.hex)

  # R sorts during merge, we need to sort to compare
  rite.sorted <- rite.h2o[order(rite.h2o$fruit),]
  row.names(rite.sorted) <- 1:6
  expect_equal(rite.sorted, rite.r)

  Log.inf("Full Outer")
  full.hex <- h2o.merge(l.hex, r.hex, T, T)
  full.r <- merge(left, right, all.x = T, all.y = T)
  full.h2o <- as.data.frame(full.hex)

  # R sorts during merge, we need to sort to compare
  full.sorted <- full.h2o[order(full.h2o$fruit),]
  row.names(dflt.sorted) <- 1:6
  expect_equal(full.sorted, full.r)

  
}

h2oTest.doTest("Verifying h2o.merge With R's Impelementation", check.merge_comparison)
