setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pub_208 <- function(localH2O) {

rdat <- read.csv(normalizePath(locate("smalldata/jira/pub_208.csv")))

Log.info("The data that R read in.")
print(rdat)

rdat$col1 <- as.factor(rdat$col1)
rdat$col5 <- as.factor(rdat$col5)
rdat$col2 <- as.factor(rdat$col2)

str(rdat)

factor_columns <- which(unlist(lapply(rdat, is.factor)))

hex <- as.h2o(rdat)

str(hex)

expect_true(is.factor(hex$col1))
expect_true(is.factor(hex$col2))
expect_true(is.factor(hex$col5))

testEnd()

}

doTest("PUB-208 as.h2o should retain which columns were factors", test.pub_208)

