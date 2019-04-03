setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  Test R function to calculate chunk size from file and cloud setting
# PUBDEV-6339
#----------------------------------------------------------------------

calcOptimalChunkSize <- function(filePath, numCols, cores, cloudSize){
    # Get maximal line size from file in bytes
    maxLineLength <- as.integer64(0)
    totalSize <- as.integer64(0)
    file_con <- file(filePath)
    for (line in readLines(file_con)){
        size <- as.integer64(nchar(line, type="bytes")+1)
        totalSize <- totalSize + size
        if (size > maxLineLength){
            maxLineLength <- size
        }
    }
    totalSize = totalSize-1
    close(file_con)

    DFLT_LOG2_CHUNK_SIZE <- 20+2;
    DFLT_CHUNK_SIZE <- bitShiftL(1, DFLT_LOG2_CHUNK_SIZE)
    localParseSize <- as.integer64(totalSize/cloudSize)
    minNumberRows <- 10 # need at least 10 rows (lines) per chunk (core)
    perNodeChunkCountLimit <- bitShiftL(1, 21) # don't create more than 2M Chunk POJOs per node
    minParseChunkSize <- bitShiftL(1, 12) # don't read less than this many bytes
    maxParseChunkSize <- bitShiftL(1, 28)-1 # don't read more than this many bytes per map() thread (needs to fit into a Value object)

    chunkSize <- as.integer64(max(c((localParseSize / (4*cores))+1, minParseChunkSize))) # lower hard limit
    if(chunkSize > 1024*1024){
        # chunkSize <- bitwAnd(chunkSize, 0xFFFFFE00) + 512 # align chunk size to 512B
        chunkSize <- (as.integer64(chunkSize/512)+1)*512
    }
    # Super small data check - file size is smaller than 64kB
    if (totalSize <= bitShiftL(1, 16)) {
        num <- minNumberRows * maxLineLength
        chunkSize <- max(c(DFLT_CHUNK_SIZE, num))
    } else {
        # Small data check
        if ((chunkSize < DFLT_CHUNK_SIZE) && ((localParseSize / chunkSize) * numCols < perNodeChunkCountLimit)) {
            num <- minNumberRows * maxLineLength
            chunkSize <- max(c(chunkSize, (minNumberRows * maxLineLength)))
        } else {
            # Adjust chunkSize such that we don't create too many chunks
            chunkCount <- cores * 4 * numCols
            if (chunkCount > perNodeChunkCountLimit) {
                ratio <- bitShiftL(1, max(c(2, log2(chunkCount / perNodeChunkCountLimit)))) # this times too many chunks globally on the cluster
                chunkSize <- as.integer64(chunkSize * ratio) # need to bite off larger chunks
            }
            chunkSize <- min(c(as.integer64(maxParseChunkSize), chunkSize)) # hard upper limit
            # if we can read at least minNumberRows and we don't create too large Chunk POJOs, we're done
            # else, fix it with a catch-all heuristic
            if (chunkSize <= (minNumberRows * maxLineLength)) {
                # might be more than default, if the max line length needs it, but no more than the size limit(s)
                # also, don't ever create too large chunks
                chunkSize <- max(c(DFLT_CHUNK_SIZE,  # default chunk size is a good lower limit for big data
                min(c(maxParseChunkSize, minNumberRows * maxLineLength)))) # don't read more than 1GB, but enough to read the minimum number of rows
            }
        }
    }
    # convert to type int
    chunkSize
}

calculateChunkSize <- function() {
    if(!"bit64" %in% installed.packages()){
        install.packages("bit64")
    }
    if(!"bitops" %in% installed.packages()){
        install.packages("bitops")
    }
    library(bitops)
    library(bit64)

    filePaths = c(locate("smalldata/arcene/arcene_train.data"),
    locate("smalldata/census_income/adult_data.csv"),
    locate("smalldata/chicago/chicagoAllWeather.csv"),
    locate("smalldata/gbm_test/alphabet_cattest.csv")
    )

    info <- capture.output(h2o.clusterInfo())

    reg <- gregexpr("(\\d+)", info[8], TRUE)
    match <-regmatches(info[8], reg)
    # Number of nodes
    cloudSize <- as.numeric(match[[1]][2])

    reg <- gregexpr("(\\d+)", info[11], TRUE)
    match <-regmatches(info[11], reg)
    # Number of cores
    cores <- as.numeric(match[[1]][2])

    for (filePath in filePaths){
        # Read data and parse setup to get number of columns 
        # if the user knows number of columns, he/she cannot parse data
        rawTrain <- h2o.importFile(path=filePath, parse=FALSE)
        setup <- h2o.parseSetup(rawTrain)

        # Get number of columns from setup, or set manually by user
        numCols <- setup$number_columns

        chunkSize <-calcOptimalChunkSize(filePath, numCols, cores, cloudSize)
        print(paste(chunkSize, setup$chunk_size))
        expect_equal(as.integer64(chunkSize), as.integer64(setup$chunk_size))
    }
}

doTest("Calculate chunk size", calculateChunkSize)
