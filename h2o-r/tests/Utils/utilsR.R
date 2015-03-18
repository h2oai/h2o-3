genString<-
function(i = NULL) {
    res <- paste(sample(letters, 26, replace = TRUE), collapse = "", sep = "")
    return(res)
}

setupRandomSeed<-
function(seed = NULL, suppress = FALSE, userDefined=FALSE) {
    test_name <- R.utils::commandArgs(asValues=TRUE)$"f"
    possible_seed_path <- paste("./Rsandbox_", test_name, "/seed", sep = "")
    if (userDefined) {
        SEED <<- seed
        set.seed(seed)
        cat("\n\n\n", paste("[INFO]: Using user defined SEED: ", seed), "\n\n\n\n")
        cat("\n\n\n [User-SEED] :", seed, "\n\n\n")
        return(seed)
    }
    if (file.exists(possible_seed_path)) {
        fileseed <- read.table(possible_seed_path)[[1]]
        cat("\n\n\n", paste("[INFO]: Reusing seed for this test from test's Rsandbox", fileseed), "\n\n\n\n")
        SEED <<- fileseed
        set.seed(fileseed)
        return(fileseed)
    }
    if (MASTER_SEED) {
        #SEED <<- seed
        cat("\n\n\n", paste("[INFO]: Using master SEED to generate a new seed for this test: ", seed), "\n\n\n\n")
        #.h2o.__logIt("[Master-SEED] :", seed, "Command")
        cat("\n\n\n [Master-SEED] :", seed, "\n\n\n")
        maxInt <- .Machine$integer.max
        newseed <- sample(maxInt, 1)
        cat("\n\n\n", paste("[INFO]: Using seed for this test ", newseed), "\n\n\n\n")
        SEED <<- newseed
        set.seed(newseed)
        return(newseed)
    }
    if (!is.null(seed)) {
        SEED <<- seed
        set.seed(seed)
        cat("\n\n\n", paste("[INFO]: Using user defined SEED: ", seed), "\n\n\n\n")
        cat("\n\n\n [User-SEED] :", seed, "\n\n\n")
        return(seed)
    } else {
        maxInt <- .Machine$integer.max
        seed <- sample(maxInt, 1)
        SEED <<- seed
        if(!suppress) {
          cat("\n\n\n", paste("[INFO]: Using SEED: ", seed), "\n\n\n\n")
          #.h2o.__logIt("[SEED] :", seed, "Command")
          cat("\n\n\n [SEED] : ", seed, "\n\n\n")
        }
        set.seed(seed)
        return(seed)
    }
    Log.info(paste("USING SEED: ", SEED))
}

cleanSummary <- function(mysum, alphabetical = FALSE) {
  # Returns string without leading or trailing whitespace
  trim <- function(x) { gsub("^\\s+|\\s+$", "", x) }
  
  lapply(1:ncol(mysum), { 
    function(i) {
      nams <- sapply(mysum[,i], function(x) { trim(unlist(strsplit(x, ":"))[1]) })
      vals <- sapply(mysum[,i], function(x) {
        numMatch <- sum(unlist(strsplit(x, "")) == ":")
        # If only one colon, then it contains numeric data
        # WARNING: This assumes categorical levels don't contain colons
        if(numMatch == 1)
          as.numeric(unlist(strsplit(x, ":"))[2])
        # Otherwise, return a string for min/max/quantile
        else {
          tmp <- unlist(strsplit(as.character(x), ":"))[-1]
          paste(tmp, collapse = ":")
        }
      })
      names(vals) <- nams
      # vals <- vals[!is.na(vals)]
      if(alphabetical) vals <- vals[order(names(vals))]
      return(vals)
    }
  })
}

checkSummary <- function(object, expected, tolerance = 1e-6) {
  sumR <- cleanSummary(expected, alphabetical = TRUE)
  sumH2O <- cleanSummary(object, alphabetical = TRUE)
  
  expect_equal(length(sumH2O), length(sumR))
  lapply(1:length(sumR), function(i) {
    vecR <- sumR[[i]]; vecH2O <- sumH2O[[i]]
    expect_equal(length(vecH2O), length(vecR))
    expect_equal(names(vecH2O), names(vecR))
    for(j in 1:length(vecR))
      expect_equal(vecH2O[j], vecR[j], tolerance = tolerance)
  })
}