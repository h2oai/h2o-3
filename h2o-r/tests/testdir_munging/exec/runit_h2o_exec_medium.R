setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')


test.eq2.h2o.exec<-
function(conn) {
    hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"), "iris.hex")


    Log.info("Print out the head of the iris dataset")
    print(hex)
    print(head(hex))

    Log.info("Add together the first two columns of iris and store in 'res1'")

    res1 <- h2o.exec(hex[,1] + hex[,2])

    Log.info("Here's the result of res1")
    print(head(res1))

    r_res1 <- iris[,1] + iris[,2]
    print(head(r_res1))
    print(head(res1)[,1])
    expect_that(head(res1)[,1], equals(head(r_res1)))

    Log.info("Try a more complicated expression:")
    Log.info("Trying: hex[,1] + hex[, 2] + hex[, 3] * hex[,4] / hex[,1]")

    res2 <- h2o.exec(expr_to_execute= hex[,1] + hex[, 2] + hex[, 3] * hex[,4] / hex[,1])

    print(head(res2))

    r_res2 <- iris[,1] + iris[,2] + iris[,3] * (iris[,4] / iris[,1])
    expect_that(head(res2)[,1], equals(head(r_res2)))

    Log.info("Try intermixing scalars: hex[,1] + hex[, 2] + hex[, 3] + (hex[,4] / 2) - (hex[,2] / hex[,1]) * 12.3")

    res3 <- h2o.exec(expr_to_execute= hex[,1] + hex[, 2] + hex[, 3] + (hex[,4] / 2) - (hex[,2] / hex[,1]) * 12.3)
    print(head(res3))

    r_res3 <- iris[,1] + iris[,2] + iris[,3] + (iris[,4] / 2) - (iris[,2] / iris[,1]) * 12.3
    expect_that(head(res3)[,1], equals(head(r_res3)))

    Log.info("Multiple column selection")

    res4 <- h2o.exec(expr_to_execute = hex[,c(1,2)] + hex[,c(2,4)])
    r_res4 <- iris[,c(1,2)] + iris[,c(2,4)]

    print(head(res4))
    expect_that(dim(res4), equals(dim(r_res4)))
    colnames(r_res4) <- paste("C", 1:dim(r_res4)[2], sep = "")
    print(head(r_res4))
    print(head(as.data.frame(res4)))
    expect_that(head(as.data.frame(res4)), equals(head(r_res4)))

    res5 <- h2o.exec(expr_to_execute = hex[,seq(1,4,1)] + hex[,seq(1,4,1)])
    r_res5 <- iris[,1:4] + iris[,1:4]

    print(head(res5))
    expect_that(dim(res5), equals(dim(r_res5)))
    colnames(r_res5) <- paste("C", 1:dim(r_res5)[2], sep = "")
    print(head(r_res5))
    print(head(as.data.frame(res5)))
    expect_that(head(as.data.frame(res5)), equals(head(r_res5)))

    res6 <- h2o.exec(expr_to_execute= ifelse(hex[,1] < 4.3, hex[,1], hex[,2] + hex[,3]))
    print(head(res6))

    r_res6 <- ifelse(iris[,1] < 4.3, iris[,1], iris[,2] + iris[,3])

    expect_that(head(res6)[,1], equals(head(r_res6)))


    res99 <- h2o.exec(res99 <- ifelse(hex[,1] < 4.3, hex[,1], hex[,2] + hex[,3]))



    Log.info("")
    Log.info("")
    Log.info("Now use column names!")
    Log.info("")
    Log.info("")


    hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"), "iris.hex")

    Log.info("Add together the first two columns of iris and store in 'res1' WITH NAMES")

    res1 <- h2o.exec(hex[,"C1"] + hex[,"C2"])

    Log.info("Here's the result of res1 WITH NAMES")
    print(head(res1))

    r_res1 <- iris[,1] + iris[,2]
    print(head(r_res1))
    print(head(res1)[,1])
    expect_that(head(res1)[,1], equals(head(r_res1)))

    Log.info("Try a more complicated expression WITH NAMES:")
    Log.info("Trying: hex[,1] + hex[, 2] + hex[, 3] * hex[,4] / hex[,1]")

    res2 <- h2o.exec(expr_to_execute= hex[,"C1"] + hex[, "C2"] + hex[, "C3"] * hex[,"C4"] / hex[,"C1"])

    print(head(res2))

    r_res2 <- iris[,1] + iris[,2] + iris[,3] * (iris[,4] / iris[,1])
    expect_that(head(res2)[,1], equals(head(r_res2)))

    Log.info("Try intermixing scalars WITH NAMES: hex[,1] + hex[, 2] + hex[, 3] + (hex[,4] / 2) - (hex[,2] / hex[,1]) * 12.3")

    res3 <- h2o.exec(expr_to_execute= hex[,"C1"] + hex[, "C2"] + hex[, "C3"] + (hex[,"C4"] / 2) - (hex[,"C2"] / hex[,"C1"]) * 12.3)
    print(head(res3))

    r_res3 <- iris[,1] + iris[,2] + iris[,3] + (iris[,4] / 2) - (iris[,2] / iris[,1]) * 12.3
    expect_that(head(res3)[,1], equals(head(r_res3)))

    Log.info("Multiple column selection WITH NAMES")

    res4 <- h2o.exec(expr_to_execute = hex[,c("C1","C2")] + hex[,c("C2","C4")])
    r_res4 <- iris[,c(1,2)] + iris[,c(2,4)]

    print(head(res4))
    expect_that(dim(res4), equals(dim(r_res4)))
    colnames(r_res4) <- paste("C", 1:dim(r_res4)[2], sep = "")
    print(head(r_res4))
    print(head(as.data.frame(res4)))
    expect_that(head(as.data.frame(res4)), equals(head(r_res4)))

    res6 <- h2o.exec(expr_to_execute= ifelse(hex[,"C1"] < 4.3, hex[,"C1"], hex[,"C2"] + hex[,"C3"]))
    print(head(res6))

    r_res6 <- ifelse(iris[,1] < 4.3, iris[,1], iris[,2] + iris[,3])

    expect_that(head(res6)[,1], equals(head(r_res6)))


    res99 <- h2o.exec(res99 <- ifelse(hex[,"C1"] < 4.3, hex[,"C1"], hex[,"C2"] + hex[,"C3"]))




    Log.info("")
    Log.info("")
    Log.info("Now use a totally different dataset!!")
    Log.info("")
    Log.info("")


    cov <- read.csv(locate("smalldata/covtype/covtype.20k.data"), header = F)

    hex <- h2o.uploadFile(conn, locate("smalldata/covtype/covtype.20k.data"), "cov")

    Log.info("Add together the first two columns of iris and store in 'res1' WITH NAMES")

    res1 <- h2o.exec(hex[,"C41"] + hex[,"C52"])

    Log.info("Here's the result of res1 WITH NAMES")
    print(head(res1))

    r_res1 <- cov[,41] + cov[,52]
    print(head(r_res1))
    print(head(res1)[,1])
    expect_that(head(res1)[,1], equals(head(r_res1)))

    Log.info("Try a more complicated expression WITH NAMES:")
    Log.info("Trying: hex[,1] + hex[, 2] + hex[, 3] * hex[,4] / hex[,1]")

    res2 <- h2o.exec(expr_to_execute= hex[,"C1"] + hex[, "C2"] + hex[, "C3"] * (hex[,"C4"] / hex[,"C1"]))

    print(head(res2))

    r_res2 <- cov[,1] + cov[,2] + cov[,3] * (cov[,4] / cov[,1])

    print(head(res2)[,1])
    print(head(r_res2))
    expect_that(head(res2)[,1], equals(head(r_res2)))

    Log.info("Try intermixing scalars WITH NAMES: hex[,1] + hex[, 2] + hex[, 3] + (hex[,4] / 2) - (hex[,2] / hex[,1]) * 12.3")

    res3 <- h2o.exec(expr_to_execute= hex[,"C1"] + hex[, "C2"] + hex[, "C3"] + (hex[,"C4"] / 2) - (hex[,"C2"] / hex[,"C1"]) * 12.3)
    print(head(res3))

    r_res3 <- cov[,1] + cov[,2] + cov[,3] + (cov[,4] / 2) - (cov[,2] / cov[,1]) * 12.3
    expect_that(head(res3)[,1], equals(head(r_res3)))

    Log.info("Multiple column selection WITH NAMES")

    res4 <- h2o.exec(expr_to_execute = hex[,c("C1","C2")] + hex[,c("C2","C4")])
    r_res4 <- cov[,c(1,2)] + cov[,c(2,4)]

    print(head(res4))
    expect_that(dim(res4), equals(dim(r_res4)))
    colnames(r_res4) <- paste("C", 1:dim(r_res4)[2], sep = "")
    print(head(r_res4))
    print(head(as.data.frame(res4)))
    expect_that(head(as.data.frame(res4)), equals(head(r_res4)))

    res6 <- h2o.exec(expr_to_execute= ifelse(hex[,"C1"] < 4.3, hex[,"C1"], hex[,"C2"] + hex[,"C3"]))
    print(head(res6))

    r_res6 <- ifelse(cov[,1] < 4.3, cov[,1], cov[,2] + cov[,3])

    expect_that(head(res6)[,1], equals(head(r_res6)))


    res99 <- h2o.exec(res99 <- ifelse(hex[,"C1"] < 4.3, hex[,"C1"], hex[,"C2"] + hex[,"C3"]))
    head(res99)

    Log.info("Try a simple case where we create new columns, do assignments, and use '$' operator")
    h2o.exec(hex$x1 <- log(hex$C1 + 1))
    head(hex)

    Log.info("Do a more complicated case where rows are also subset")
    h2o.exec(hex <- hex[ hex$x1 < 1.3, "C2"])
    head(hex)

    Log.info("Now try it with the `[` operator")
    hex <- h2o.uploadFile(conn, locate("smalldata/airlines/allyears2k_headers.zip"), "flights.hex")
    print(head(h2o.exec(hex[,"newcol1"] <- ifelse(hex$IsDepDelayed == "YES", 1,0))))
    print(head(hex))

    testEnd()
}

doTest("Test h2o.exec(client, expr, key)", test.eq2.h2o.exec)

