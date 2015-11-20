


test.slice.div <- function() {
  hex <- as.h2o(iris)

  #hex <- as.h2o(iris)
  Log.info("Try /ing a scalar to a numeric column: 5 / hex[,col]")
  col <- sample(ncol(hex)-1, 1)

  sliced <- hex[,col]
  print(sliced)
  print(head(sliced))
  Log.info("Placing key \"sliced.hex\" into User Store")
#  sliced <- h2o.assign(sliced, "sliced.hex")

  Log.info("/ing 5 to sliced.hex")
  slicedDivFive <- sliced / 5

#  slicedDivFive <- h2o.assign(slicedDivFive, "slicedDivFive.hex")

  Log.info("Orignal sliced: ")
  print(head(as.data.frame(sliced)))

  Log.info("Sliced / 5: ")
  print(head(as.data.frame(slicedDivFive)))
  expect_that(as.data.frame(slicedDivFive), equals(as.data.frame(sliced) / 5))

  Log.info("Checking left and right: ")
  slicedDivFive <- sliced / 5

  fiveDivSliced <- 5 / sliced

  Log.info("sliced / 5: ")
  print(head(slicedDivFive))

  Log.info("5 / sliced: ")
  print(head(fiveDivSliced))

  Log.info("Checking the variation of H2OFrame / H2OFrame")
  hexDivHex <- fiveDivSliced / slicedDivFive

  Log.info("FiveDivSliced / slicedDivFive: ")
  print(head(hexDivHex))
  Log.info("head(as.data.frame(fiveDivSliced)/as.data.frame(slicedDivFive))")
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

doTest("BINOP2 EXEC2 TEST: /", test.slice.div)

