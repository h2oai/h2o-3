setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub_506 <- function() {

hex <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/jira/pub_506.csv")), "p506")

rdat <- read.csv(normalizePath(h2oTest.locate("smalldata/jira/pub_506.csv")))

h2oTest.logInfo("The data that R read in.")
print(rdat)

h2oTest.logInfo("The data that H2O read in.")
print(hex)

expect_equal(as.data.frame(hex[1,1])[1,1], rdat[1,1])
expect_equal(as.data.frame(hex[2,1])[1,1], rdat[2,1])
expect_equal(as.data.frame(hex[3,1])[1,1], rdat[3,1])
expect_equal(as.data.frame(hex[4,1])[1,1], rdat[4,1])
expect_equal(as.data.frame(hex[5,1])[1,1], rdat[5,1])
expect_equal(as.data.frame(hex[6,1])[1,1], rdat[6,1])
expect_equal(as.data.frame(hex[7,1])[1,1], rdat[7,1])
expect_equal(as.data.frame(hex[8,1])[1,1], rdat[8,1])

sum_h2o <- sum(hex)
sum_R   <- sum(rdat)

print(sum_h2o)
print(sum_R)

expect_equal(sum_h2o, sum_R)

h2oTest.logInfo("Now doing c(3000000000, 3000000001)")

a_h2o <- as.h2o(c(3000000000, 3000000001), 'a')
a_R   <- c(3000000000, 3000000001) 

h2oTest.logInfo("H2O's:")
print(a_h2o)
h2oTest.logInfo("R's:")
print(a_R)

expect_equal(as.data.frame(a_h2o[1,1])[1,1], a_R[1])
expect_equal(as.data.frame(a_h2o[2,1])[1,1], a_R[2])

h2oTest.logInfo("Expect their sums to be equal:")

print(sum(a_h2o))
print(sum(a_R))

expect_equal(sum(a_h2o), sum(a_R))




}

h2oTest.doTest("PUB-507 H2O does not parse numbers correctly", test.pub_506)

