


test.pub_505 <- function() {

hex <- h2o.importFile(normalizePath(locate("smalldata/jira/pub_505.csv")), "p505")

rdat <- read.csv(normalizePath(locate("smalldata/jira/pub_505.csv")))

Log.info("The data that R read in.")
print(rdat)

Log.info("The data that H2O read in.")
print(hex)

expect_equal(as.data.frame(hex[1,1])[1,1], rdat[1,1])
expect_equal(as.data.frame(hex[1,2])[1,1], rdat[1,2])

sum_h2o <- sum(hex)
sum_R   <- sum(rdat)

print(sum_h2o)
print(sum_R)

expect_equal(sum_h2o, sum_R)



}

doTest("PUB-505 H2O does not parse numbers correctly", test.pub_505)

