setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pub_575 <- function(localH2O) {

covtype.hex <- h2o.importFile(normalizePath(locate("smalldata/covtype/covtype.20k.data")), "cov")

hex <- covtype.hex

print(ifelse(TRUE, hex, hex[,1] <- hex[,1] + 1))
print(ifelse(1, hex, hex[,1] <- hex[,1] + 1))


#ensure that base ifelse is not broken
print(ifelse(TRUE, iris, iris[,1] <- iris[,1] + 1))

testEnd()

}

doTest("PUB-575 ifelse with embedded assignment", test.pub_575)

