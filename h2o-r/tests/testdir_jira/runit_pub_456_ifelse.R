


test.pub.456 <- function() {
  a <- as.h2o(iris)

  a[,1] <- ifelse(a[,1] == 0, 54321, 54321)
   
  
}

doTest("Test pub 456", test.pub.456)
