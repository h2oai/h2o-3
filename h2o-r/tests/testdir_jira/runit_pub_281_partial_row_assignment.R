


test.head_empty_frame <- function() {

  hex <- as.h2o(iris)
  print(hex)

  hex[1,] <- 3.3
  
  print(hex)
   
  
}

doTest("Test frame add.", test.head_empty_frame)
