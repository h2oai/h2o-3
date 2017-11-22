setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# PUBDEV_4727: Groupby median fix
##

test <- function(conn) {
    tot = 1e-10
    Log.info("Generateing random dataset ...")
    numrows <- 10000000
    numCols <- 4
    numColEnd <- 3 + numCols - 1
    dfEnum.hex <- h2o.createFrame(rows = numrows, col = 2, categorical_fraction = 1, integer_fraction = 0, binary_fraction = 0,
    time_fraction = 0, string_fraction = 0, missing_fraction = 0.0, has_response = FALSE, factor = 5)
    dfReal.hex <- h2o.createFrame(rows = numrows, col = numCols, categorical_fraction = 0, integer_fraction = 0, binary_fraction = 0,
    time_fraction = 0, string_fraction = 0, missing_fraction = 0.0, has_response = FALSE)  # real numbers
    df.hex <- h2o.cbind(dfEnum.hex, dfReal.hex)
    column_names <- names(df.hex)
    print("Number of rows in the data frame is: ")
    print(nrow(df.hex))
    # throw in a bunch of other junks just to check
    aggregated_median <- h2o.group_by(data = df.hex, by = column_names[1 : 2], median(3), median(4), median(5), median(6), mean(3), sd(4), gb.control = list(na.methods = "rm"))
    h2o_groupby_median <- as.data.frame(aggregated_median)

    # generate R median and compare h2o.groupby median with it
    c1Vals <- h2o_groupby_median$C1
    c2Vals <- h2o_groupby_median$C2
    temp <- as.data.frame(df.hex)
 
    for (colIndex in c(3 : numColEnd)) {
        for (index in c(1 : nrow(h2o_groupby_median))) {
            temp2 <- temp[temp$C1 == c1Vals[index], 1 : numColEnd]
            temp3 <- temp2[temp2$C2 == c2Vals[index], 1 : numColEnd]
            tempCol <- temp3[, colIndex]
            rmedian <- median(tempCol)  # R median for one of the groups
            errText <- paste0("H2O groupby mean is ", h2o_groupby_median[index, colIndex], ". R median is ", rmedian, ".  They are not the same.")
            expect_true(abs(h2o_groupby_median[index, colIndex] - rmedian) < tot, errText)
        }
    }
}

doTest("Testing groupby median", test)

