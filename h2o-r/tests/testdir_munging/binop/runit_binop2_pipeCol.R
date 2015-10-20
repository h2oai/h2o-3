


test.binop2.pipe <- function() {
 hex <- as.h2o(iris)

  Log.info("Selecting a column")
  #col <- sample(colnames[colTypes != "enum"], 1)
  #col <- ifelse(is.na(suppressWarnings(as.numeric(col))), col, as.numeric(col))
  #col <- ifelse(is.na(suppressWarnings(as.numeric(col))), col, paste("C", col+1, sep = "", collapse = ""))
  df <- head(hex)

  col <- sample(ncol(hex), 1)

  sliced <- hex[,col]

  Log.info("Performing the binop2 operation: 5 | col")
  Log.info("Expectation is the following: ")
  Log.info("For a non-enum column, ANDing with a single number will result in a column of booleans.")
  Log.info("TRUE is returned always")
  Log.info("This is checked on both the left and the right (which produce the same boolean vec).")

  newHex <- 5 | sliced

  expect_that(dim(newHex), equals(dim(sliced)))

  print(head(newHex))
  print(head(as.data.frame(sliced) | 5))
  
  
}

doTest("Binop2 EQ2 Test: |", test.binop2.pipe)

