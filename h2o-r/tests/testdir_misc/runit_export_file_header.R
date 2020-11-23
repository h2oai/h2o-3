setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#Export file with h2o.export_file with a custom header configuration

read.first.line <- function(fileName) {
    conn <- file(fileName, open="r")
    line <- readLines(conn, n=1)
    close(conn)
    return(line)
}

test.export.file.header <- function() {
    frame_r <- data.frame(C1 = c(11, 12), C2 = c(21, 22))
    frame_hex <- as.h2o(frame_r)
    
    target_default <- file.path(sandbox(), "export_default_header.csv")
    target_no_header <- file.path(sandbox(), "export_no_header.csv")
    target_no_quotes <- file.path(sandbox(), "export_no_quotes.csv")

    h2o.exportFile(frame_hex, target_default)
    h2o.exportFile(frame_hex, target_no_header, header=FALSE)
    h2o.exportFile(frame_hex, target_no_quotes, quote_header=FALSE)

    expect_equal(read.first.line(target_default), '"C1","C2"')
    expect_equal(read.first.line(target_no_header), '11,21')
    expect_equal(read.first.line(target_no_quotes), 'C1,C2')
}

doTest("Testing Exporting Files (custom header)", test.export.file.header)
