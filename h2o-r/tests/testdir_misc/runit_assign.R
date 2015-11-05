##
# Test assigned NAs
##

test <- function() {
	iris <- as.h2o(iris)
	numNAs <- 40
  s <- sample(nrow(iris),numNAs)
  iris[s,5] <- NA
  print(summary(iris))
  expect_that(sum(is.na(iris[5])), equals(numNAs))
  
}

doTest("Count assigned NAs", test)

