setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################
# Test for HEX-1829
# histograms in R
######################################################################


options(echo=TRUE)


test.hex_1829 <- function(){

  h2oTest.heading("BEGIN TEST")
  h2oTest.logInfo("Import small prostate dataset")
  path <- h2oTest.locate("smalldata/logreg/prostate.csv")
  hex <- h2o.importFile(path, destination_frame="p.hex")

  h2oTest.logInfo("Create small numeric vectors")
  age <- hex$AGE
  age.R <- as.data.frame(age)
  vol <- hex$VOL
  vol.R <- as.data.frame(vol)
  qx  <- quantile(vol$VOL, probs = c(0.4,0.6,0.8,1.0)) 
  
  run_check_hist <- function(col.h2o, col.R, colname, breaks){
    ## Create histograms in R and H2O
    h2oTest.logInfo(paste("Create histograms in R and H2O for", colname ,"column"))
    
    if(missing(breaks)) {
      h2o_hist <- h2o.hist(col.h2o[,colname])
      r_hist   <- hist(col.R[,colname])
    } else {
      h2o_hist <- h2o.hist(col.h2o[,colname], breaks = breaks)
      r_hist   <- hist(col.R[,colname], breaks = breaks)
    }    
    
    h2oTest.logInfo("Check histogram components")
    expect_equal(h2o_hist$breaks, r_hist$breaks)
    expect_equal(h2o_hist$counts, r_hist$counts)
    expect_equal(h2o_hist$density, r_hist$density)
    expect_equal(h2o_hist$mids, r_hist$mids)
  }

  run_check_hist(vol, vol.R, "VOL")
  run_check_hist(age, age.R, "AGE")
  run_check_hist(age, age.R, "AGE", breaks = c(43, 60, 70, 80))
  run_check_hist(vol, vol.R, "VOL", breaks = as.numeric(qx))
  
  
}

h2oTest.doTest("HEX-1829 Test: Create histograms in R from H2OH2OFrame objects", test.hex_1829)
