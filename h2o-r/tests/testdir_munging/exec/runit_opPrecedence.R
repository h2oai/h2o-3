setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

get.eval.result <- function(conn, expr) {
    res =  .h2o.__exec2(conn, expr)
    return(new("h2o.frame", h2o=conn, key=res$dest_key))
}

test.op.precedence <- function(conn) {
    a = sample(10)
    b = sample(10)
    c = sample(10)
    A = as.h2o(conn, a, "A")
    B = as.h2o(conn, b, "B")
    C = as.h2o(conn, c, "C")
    s1 = a + b * c
    s2 = a - b - c
    s3 = a ^ 2 ^ 3
    s4 = a == b & c
    s5 = a == b + c
    s6 = a | b & c

    Log.info("Check A + B * C.")
    S1 <- as.data.frame(A + B * C)

    print(S1)
    print(s1)

    expect_that(all(S1 == s1), equals(T))

    Log.info("Check A - B - C.")
    S2 <- as.data.frame(A - B - C)
    expect_that(all(S2 == s2), equals(T))

    Log.info("Check A ^ 2 ^ 3.")
    S3 <- as.data.frame(A ^ 2 ^ 3)
    expect_that(all(S3 == s3), equals(T))

    Log.info("Check A == B & C.")
    S4 <- as.data.frame(A == B & C)
    expect_that(all(S4 == s4), equals(T))

    Log.info("Check A == B + C.")
    S5 <- as.data.frame(A == B + C)
    expect_that(all(S5 == s5), equals(T))

    Log.info("Check A | B & C.")
    S6 <- as.data.frame(A | B & C)
    expect_that(all(S6 == s6), equals(T))

    testEnd()
}

doTest("Test operator precedence.", test.op.precedence)
