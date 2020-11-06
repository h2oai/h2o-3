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

test_date_handling <- function() {
  h2o_df <- h2o.uploadFile(locate("smalldata/timeSeries/CreditCard-ts_train.csv"))
  r_df <- read.csv(locate("smalldata/timeSeries/CreditCard-ts_train.csv"),
                   colClasses = list(MONTH = "POSIXct"))

  str(as.numeric(r_df$MONTH[[1]]))
  # => 1112306400
  str(as.numeric(as.data.frame(h2o_df)$MONTH[[1]]))
  # => 1112313600000

  # "MONTH" contains date with first day of a month, e.g., 2015-05-01
  expect_true(all(as.data.frame(h2o_df)$MONTH == r_df$MONTH))
}

doSuite("Test data frame", makeSuite(
  test,
  test_date_handling
))

