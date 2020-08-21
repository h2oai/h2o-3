setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing number of rows in as.data.frame 
##




# setupRandomSeed(1994831827)

test <- function() {
    # For interactive debugging.
    # conn = h2o.init()
    
    Log.info("Reading prostate into R")	
	x = read.csv(locate("smalldata/logreg/prostate.csv"), header=T)
	Log.info("Parsing prostate into H2O")	
	hex = h2o.importFile(locate("smalldata/logreg/prostate.csv"), "hex")
	Nhex = as.data.frame(hex)
	
	Log.info("Expect that number of rows in as.data.frame is same as the original file")
    print(sprintf("nrow(Nhex): %d", nrow(Nhex)))
    print(sprintf("nrow(x): %d", nrow(x)))
	expect_that(nrow(Nhex), equals(nrow(x)))

    
    df <- data.frame(
      c1 = c(1.1, 2.22, 3.345, 4.678, 5.098765),
      c2 = c("one", "with, sep", "with\"\"quotes\"", "\"", "quoted\",\"sep")
    )

    # options(h2o.fread=TRUE) # uncomment to test with data-table but it will fail
	frames <- list(df, data.frame(c=df[, 2]))
    for (original in frames) {
        print("Original:")
        print(original)
        h2o_fr <- as.h2o(original)
        as_df <- as.data.frame(h2o_fr)
        print("Converted:")
        print(as_df)
	    expect_true(all(as_df == original))
    }
}

doTest("Test data frame", test)

