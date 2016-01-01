setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Run through groupby methods with different column types
##

test <- function(conn) {
  h2oTest.logInfo("Upload prostate dataset into H2O...")
  df.hex <- h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate.csv"))
  h2oTest.logInfo("Import airlines dataset into R...")
  df.R   <- read.csv(h2oTest.locate("smalldata/prostate/prostate.csv"))
  races <- lapply(0:2, function(x) df.R[df.R$RACE == x, "VOL" ])
    
  h2oTest.logInfo("Test method = nrow...")
  gp_nrow <- h2o.group_by(data = df.hex, by = "RACE", nrow("VOL"))
  gp_nrow <- as.data.frame(gp_nrow)[,2]
  r_nrow  <- sapply(races, length)
  checkEqualsNumeric(gp_nrow, r_nrow)
  
  h2oTest.logInfo("Test method = sum...")
  gp_sum <- h2o.group_by(data = df.hex, by = "RACE", sum("VOL"))
  gp_sum <- as.data.frame(gp_sum)[,2]
  r_sum  <- sapply(races, sum)
  checkEqualsNumeric(r_sum, gp_sum)
   
  h2oTest.logInfo("Test method = mean ...")
  gp_mean <- h2o.group_by(data = df.hex, by = "RACE", mean("VOL"))
  gp_mean <- as.data.frame(gp_mean)[,2]
  r_mean  <- sapply(races, mean)
  checkEqualsNumeric(r_mean, gp_mean)

#   Unimplemented at the moment - refer to jira: https://0xdata.atlassian.net/browse/PUBDEV-2319
#   h2oTest.logInfo("Test method = median ...")
#   gp_median <- h2o.group_by(data = df.hex, by = "RACE", median("VOL"))
#   gp_median <- as.data.frame(gp_median)[,2]
#   r_median  <- sapply(races, median)
#   checkEqualsNumeric(r_median, gp_median)
  
  h2oTest.logInfo("Test method = var ...")
  gp_var <- h2o.group_by(data = df.hex, by = "RACE", var("VOL"))  
  gp_var <- as.data.frame(gp_var)[,2]
  r_var  <- sapply(races, var)
  checkEqualsNumeric(r_var, gp_var)
  
  h2oTest.logInfo("Test method = sd ...")
  gp_sd <- h2o.group_by(data = df.hex, by = "RACE", sd("VOL"))  
  gp_sd <- as.data.frame(gp_sd)[,2]
  r_sd  <- sapply(races, sd)
  checkEqualsNumeric(r_sd, gp_sd)
  
}

h2oTest.doTest("Testing different methods for groupby:", test)

