######################################################################
# Test for HEX-1829
# histograms in R
######################################################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../h2o-runit.R')

test.hex_1829 <- function(conn){

  heading("BEGIN TEST")
  Log.info("Import small prostate dataset")
  path <- locate("smalldata/logreg/prostate.csv")
  hex <- h2o.importFile(conn, path, key="p.hex")

  Log.info("Create small numeric vectors")
  age <- hex$AGE
  age.R <- as.data.frame(age)
  vol <- hex$VOL
  vol.R <- as.data.frame(vol)
  
  run_check_hist <- function(col.h2o, col.R, colname){
    ## Create histograms in R and H2O
    Log.info(paste("Create histograms in R and H2O for", colname ,"column"))
    h2o_hist <- hist(col.h2o)
    r_hist <- hist(col.R[,colname])
    
    Log.info("Check histogram components")
    expect_equal(h2o_hist$breaks[-1], r_hist$breaks)
    expect_equal(sum(h2o_hist$counts), sum(r_hist$counts))
    expect_equal(sum(h2o_hist$density), sum(r_hist$density))
    expect_equal(sum(h2o_hist$density), sum(r_hist$density))
    expect_equal(h2o_hist$mids, r_hist$mids)
    expect_equal(sum(h2o_hist$density), sum(r_hist$density))
  }

  run_check_hist(age, age.R, "AGE")
  run_check_hist(vol, vol.R, "VOL")
  
  testEnd()
}

doTest("HEX-1829 Test: Create histograms in R from h2o.frame objects", test.hex_1829)