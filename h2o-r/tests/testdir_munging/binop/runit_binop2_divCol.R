setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.slice.div <- function() {
  hex <- as.h2o(iris)

  #hex <- as.h2o(iris)
  h2oTest.logInfo("Try /ing a scalar to a numeric column: 5 / hex[,col]")
  col <- sample(ncol(hex)-1, 1)

  sliced <- hex[,col]
  print(sliced)
  print(head(sliced))
  h2oTest.logInfo("Placing key \"sliced.hex\" into User Store")
#  sliced <- h2o.assign(sliced, "sliced.hex")

  h2oTest.logInfo("/ing 5 to sliced.hex")
  slicedDivFive <- sliced / 5

#  slicedDivFive <- h2o.assign(slicedDivFive, "slicedDivFive.hex")

  h2oTest.logInfo("Orignal sliced: ")
  print(head(as.data.frame(sliced)))

  h2oTest.logInfo("Sliced / 5: ")
  print(head(as.data.frame(slicedDivFive)))
  expect_that(as.data.frame(slicedDivFive), equals(as.data.frame(sliced) / 5))

  h2oTest.logInfo("Checking left and right: ")
  slicedDivFive <- sliced / 5

  fiveDivSliced <- 5 / sliced

  h2oTest.logInfo("sliced / 5: ")
  print(head(slicedDivFive))

  h2oTest.logInfo("5 / sliced: ")
  print(head(fiveDivSliced))

  h2oTest.logInfo("Checking the variation of H2OH2OFrame / H2OH2OFrame")
  hexDivHex <- fiveDivSliced / slicedDivFive

  h2oTest.logInfo("FiveDivSliced / slicedDivFive: ")
  print(head(hexDivHex))
  h2oTest.logInfo("head(as.data.frame(fiveDivSliced)/as.data.frame(slicedDivFive))")
  print(head(as.data.frame(fiveDivSliced)/as.data.frame(slicedDivFive)))
  A <- na.omit(data.frame(na.omit(as.data.frame(hexDivHex))))
  B <- na.omit(data.frame(na.omit(as.data.frame(fiveDivSliced)) / na.omit(as.data.frame(slicedDivFive) ) ))

  cat("\n\n\n FRAME A:")
  print(A)
  cat("\n\n\n FRAME B:")
  print(B)
  cat("\n\n\n")

  if (dim(A)[1] == 0 || dim(B) == 0) {
    # all NAs in the datasets...
    expect_true(dim(A)[1] == dim(B)[1])
    expect_true(dim(A)[2] == dim(B)[2])
  } else {
    D <- cbind(A,B)
    C <- sum(A == B)
    print(C)
    print(A[A != B,])
    print(B[A != B,])

    res <- sum(na.omit(D[,1] - D[,2]))
    expect_true( res < 1E-4 || C == nrow(A))
  }


}

h2oTest.doTest("BINOP2 EXEC2 TEST: /", test.slice.div)

