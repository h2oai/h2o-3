setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Run through groupby methods with different column numbers
##

test <- function(conn) {
  Log.info("Upload prostate dataset into H2O...")
  df.hex <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv"))
  Log.info("Import airlines dataset into R...")
  df.R   <- read.csv(locate("smalldata/prostate/prostate.csv"))
  dprosdcapsVol <- lapply(0:2, function(x) df.R[df.R$RACE == x, c("VOL") ])
  raceVol <- lapply(0:2, function(x) df.R[df.R$RACE == x, c("VOL") ])
  raceAge <- lapply(0:2, function(x) df.R[df.R$RACE == x, c("AGE") ])
  racePSA <- lapply(0:2, function(x) df.R[df.R$RACE == x, c("PSA") ])
  
  # perform group by on subsets of columns
  gp_sd <- h2o.group_by(data = df.hex, by = c("RACE") ,sd(c("VOL", "AGE", "PSA", "RACE")), mean(c("VOL", "AGE", "PSA")))
  gp_vol <- as.data.frame(gp_sd)[,2]
  gp_age <- as.data.frame(gp_sd)[,3]
  gp_psa <- as.data.frame(gp_sd)[,4]
  mean_vol <- as.data.frame(gp_sd)[,6]
  mean_age <- as.data.frame(gp_sd)[,7]
  mean_psa <- as.data.frame(gp_sd)[,8]
  
  # get R metrics
  r_vol  <- sapply(raceVol, sd)
  r_age <- sapply(raceAge, sd)
  r_psa <- sapply(racePSA, sd)
  r_mean_vol  <- sapply(raceVol, mean)
  r_mean_age <- sapply(raceAge, mean)
  r_mean_psa <- sapply(racePSA, mean)
  
  # compare R and H2O groupby results
  checkEqualsNumeric(gp_vol, r_vol)
  checkEqualsNumeric(gp_age, r_age)
  checkEqualsNumeric(gp_psa, r_psa)
  checkEqualsNumeric(mean_vol, r_mean_vol)
  checkEqualsNumeric(mean_age, r_mean_age)
  checkEqualsNumeric(mean_psa, r_mean_psa)
  
  # make sure single column still works
  gp_sd <- h2o.group_by(data = df.hex, by = c("RACE") ,sd("VOL"), mean("AGE"))
  gp_1col_sd <- as.data.frame(gp_sd)[,2]
  gp_1col_mean <- as.data.frame(gp_sd)[,3]
  checkEqualsNumeric(gp_1col_sd, r_vol)
  checkEqualsNumeric(gp_1col_mean, r_mean_age)
  
  # do not specify action columns, should return all columns
  gp_empty <- h2o.group_by(data = df.hex, by = c("RACE") ,sd(), mean())
  gp_all <- h2o.group_by(data=df.hex, by=c("RACE"), sd(c("ID","CAPSULE","AGE","DPROS","DCAPS","PSA","VOL","GLEASON")), 
                         mean(c("ID","CAPSULE","AGE","DPROS","DCAPS","PSA","VOL","GLEASON")))
  # make sure the two frames are equal
  compareFrames(gp_empty, gp_all, prob=1, tolerance=1e-10)
  
}

doTest("Testing different methods for groupby:", test)

