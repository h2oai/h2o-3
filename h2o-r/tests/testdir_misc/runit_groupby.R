##
# Run through groupby methods with different column types
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test <- function(conn) {
  Log.info("Upload prostate dataset into H2O...")
  df.hex <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv"))
  Log.info("Import airlines dataset into R...")
  df.R   <- read.csv(locate("smalldata/prostate/prostate.csv"))
  
  Log.info("Test method = nrow...")
  gp_nrow <- h2o.group_by(data = df.hex, by = "RACE", order.by = "RACE", nrow("VOL"))
  Log.info("Test method = sum...")
  gp_sum  <- h2o.group_by(data = df.hex, by = "RACE", order.by = "RACE", sum("VOL"))
  Log.info("Test method = mean ...")
  gp_mean <- h2o.group_by(data = df.hex, by = "RACE", order.by = "RACE", mean("VOL"))
  Log.info("Test method = median ...")
  gp_median <- h2o.group_by(data = df.hex, by = "RACE", order.by = "RACE", median("VOL"))
  Log.info("Test method = mode ...")
  df.hex[, "AGE"] <- as.factor(df.hex[, "AGE"])
  gp_mode <- h2o.group_by(data = df.hex, by = "RACE", order.by = "RACE", mode("AGE"))
  
  
#   Log.info("Test method = nrow, sum, mean, median, and mode  for the dataset...")
#   df.hex[, "AGE"] <- as.factor(df.hex[, "AGE"])
#   groupby <- h2o.group_by(data = df.hex, by = "RACE",  order.by = "RACE",
#                            nrow("ID"), sum("VOL"), mean("GLEASON"), median("DPROS"), mode("AGE"))  
  testEnd()
}

doTest("Testing different methods for groupby:", test)

