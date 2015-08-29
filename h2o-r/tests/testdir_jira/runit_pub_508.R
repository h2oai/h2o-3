setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pub_508 <- function() {

hex <- h2o.importFile(normalizePath(locate("smalldata/jira/pub_508.csv")), "p508")

rdat <- read.csv(normalizePath(locate("smalldata/jira/pub_508.csv")))

Log.info("The data that R read in.")
print(rdat)

Log.info("The data that H2O read in.")
print(hex)

expect_equal(as.data.frame(hex[1,1])[1,1], rdat[1,1])
expect_equal(as.data.frame(hex[2,1])[1,1], rdat[2,1])

sum_h2o <- sum(hex)
sum_R   <- sum(rdat)

print(sum_h2o)
print(sum_R)

expect_equal(sum_h2o, sum_R)

testEnd()

}

doTest("PUB-508 H2O does not parse numbers correctly", test.pub_508)

