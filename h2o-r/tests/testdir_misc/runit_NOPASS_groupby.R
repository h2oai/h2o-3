##
# Run through groupby methods with different column types
##

test <- function(conn) {
  Log.info("Upload prostate dataset into H2O...")
  df.hex <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv"))
  Log.info("Import airlines dataset into R...")
  df.R   <- read.csv(locate("smalldata/prostate/prostate.csv"))
  races <- lapply(0:2, function(x) df.R[df.R$RACE == x, "VOL" ])
    
  Log.info("Test method = nrow...")
  gp_nrow <- h2o.group_by(data = df.hex, by = "RACE", order.by = "RACE", nrow("VOL"))
  gp_nrow <- as.data.frame(gp_nrow)[,2]
  r_nrow  <- sapply(races, length)
  checkEqualsNumeric(gp_nrow, r_nrow)
  
  Log.info("Test method = sum...")
  gp_sum <- h2o.group_by(data = df.hex, by = "RACE", order.by = "RACE", sum("VOL"))
  gp_sum <- as.data.frame(gp_sum)[,2]
  r_sum  <- sapply(races, sum)
  checkEqualsNumeric(r_sum, gp_sum)
   
  Log.info("Test method = mean ...")
  gp_mean <- h2o.group_by(data = df.hex, by = "RACE", order.by = "RACE", mean("VOL"))
  gp_mean <- as.data.frame(gp_mean)[,2]
  r_mean  <- sapply(races, mean)
  checkEqualsNumeric(r_mean, gp_mean)
  
  Log.info("Test method = median ...")
  gp_median <- h2o.group_by(data = df.hex, by = "RACE", order.by = "RACE", median("VOL"))
  gp_median <- as.data.frame(gp_median)[,2]
  r_median  <- sapply(races, median)
  checkEqualsNumeric(r_median, gp_median)
  
  Log.info("Test method = var ...")
  gp_var <- h2o.group_by(data = df.hex, by = "RACE", order.by = "RACE", var("VOL"))  
  gp_var <- as.data.frame(gp_var)[,2]
  r_var  <- sapply(races, var)
  checkEqualsNumeric(r_var, gp_var)
  
  Log.info("Test method = sd ...")
  gp_sd <- h2o.group_by(data = df.hex, by = "RACE", order.by = "RACE", sd("VOL"))  
  gp_sd <- as.data.frame(gp_sd)[,2]
  r_sd  <- sapply(races, sd)
  checkEqualsNumeric(r_sd, gp_sd)
  
}

doTest("Testing different methods for groupby:", test)

