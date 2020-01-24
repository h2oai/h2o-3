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
	
	# Quote writing
	original <- data.frame(
	  ngram = c(
	    "SIRET:417 653 698",
	    "SIRET:417 653 698 00031",
	    "Sans",
	    "Sans esc.",
	    "Sans esc. jusqu\"\"au", # Two quotes in line
	    "Sans esc. jusqu\"au 15.11.2018"
	  )
	)
	print("Original data")
	print(original)
	
	h2o_fr <- as.h2o(original)
	print("H2O Frame")
	print(h2o_fr)
	
	as_df <- as.data.frame(h2o_fr)
	print("As data frame:")
	print(as_df)
	
	expect_true(all(as_df == original))
      
    
}

doTest("Test data frame", test)

