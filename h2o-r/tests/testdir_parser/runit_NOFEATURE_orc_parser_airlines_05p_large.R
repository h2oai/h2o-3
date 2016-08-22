setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
################################################################################
##
## This tests Orc multifile parser by comparing the summary of the original csv frame with the h2o parsed orc frame
##
################################################################################


test.continuous.or.categorical <- function() {


	original = h2o.importFile(locate("bigdata/laptop/airlines_all.05p.csv"),destination_frame = "original",
                     col.types=c("Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Enum","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Enum"))
	print("************** csv parsing time: ")
	ptm <- proc.time()
	csv = h2o.importFile(locate("bigdata/laptop/parser/orc/pubdev_3200/air05_csv"),destination_frame = "csv",col.names = names(original),
                     col.types=c("Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Enum","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Enum"))
  timepassed = proc.time() - ptm
  print(timepassed)
  
  print("************** orc parsing time without forcing column types: ")
  ptm <- proc.time()
  orc2 = h2o.importFile(locate("bigdata/laptop/parser/orc/pubdev_3200/air05_orc"),destination_frame = "orc2",col.names = names(original))
  timepassed = proc.time()-ptm
  print(timepassed)
  h2o.rm(orc2)
  
  print("************** orc parsing time forcing same column types as csv: ")
  ptm <- proc.time()
		orc = h2o.importFile(locate("bigdata/laptop/parser/orc/pubdev_3200/air05_orc"),destination_frame = "orc",col.names = names(original),
                     col.types=c("Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Enum","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Enum"))
  timepassed = proc.time()-ptm

  
  print(timepassed)
  
  	expect_equal(summary(csv),summary(original))
  	
  	for(i in 1:ncol(csv)){
       print(i)
       expect_equal(summary(csv[,i]),summary(orc[,i]))
    }
}

doTest("Test orc multifile parser", test.continuous.or.categorical)
