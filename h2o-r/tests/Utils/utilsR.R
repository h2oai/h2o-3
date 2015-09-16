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
        if(is.na(numMatch) || numMatch <= 1)
          as.numeric(unlist(strsplit(x, ":"))[2])
        # Otherwise, return a string for min/max/quantile
        else {
          tmp <- unlist(strsplit(as.character(x), ":"))[-1]
          paste(tmp, collapse = ":")
        }
      })
      names(vals) <- nams
      vals <- vals[!is.na(nams)]
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

genDummyCols <- function(df, use_all_factor_levels = TRUE) {
  NUM <- function(x) { x[,sapply(x, is.numeric)] }
  FAC <- function(x) { x[,sapply(x, is.factor)]  }
  FAC_LEVS <- function(x) { sapply(x, function(z) { length(levels(z)) })}
  
  df_fac <- data.frame(FAC(df))
  if(ncol(df_fac) == 0) {
    DF <- data.frame(NUM(df))
    names(DF) <- colnames(df)[which(sapply(df, is.numeric))]
  } else {
    if(!"ade4" %in% rownames(installed.packages())) install.packages("ade4")
    require(ade4)
    
    df_fac_acm <- acm.disjonctif(df_fac)
    if (!use_all_factor_levels) {
      fac_offs <- cumsum(c(1, FAC_LEVS(df_fac)))
      fac_offs <- fac_offs[-length(fac_offs)]
      df_fac_acm <- data.frame(df_fac_acm[,-fac_offs])
    }
    DF <- data.frame(df_fac_acm, NUM(df))
    fac_nams <- mapply(function(x, cname) { 
      levs <- levels(x)
      if(!use_all_factor_levels) levs <- levs[-1]
      paste(cname, levs, sep = ".") }, 
      df_fac, colnames(df)[which(sapply(df, is.factor))])
    fac_nams <- as.vector(unlist(fac_nams))
    fac_range <- 1:ncol(df_fac_acm)
    names(DF)[fac_range] <- fac_nams
    
    if(ncol(NUM(df)) > 0) {
      num_range <- (ncol(df_fac_acm)+1):ncol(DF)
      names(DF)[num_range] <- colnames(df)[which(sapply(df, is.numeric))]
    }
  }
  
  return(DF)
}

alignData <- function(df, center = FALSE, scale = FALSE, ignore_const_cols = TRUE, use_all_factor_levels = TRUE) {
  df.clone <- df
  is_num <- sapply(df.clone, is.numeric)
  if(any(is_num)) {
    df.clone[,is_num] <- scale(df.clone[,is_num], center = center, scale = scale)
    df.clone <- df.clone[, c(which(!is_num), which(is_num))]   # Move categorical column to front
  }
  
  if(ignore_const_cols) {
    is_const <- sapply(df.clone, function(z) { var(z, na.rm = TRUE) == 0 })
    if(any(is_const))
      df.clone <- df.clone[,!is_const]
  }
  genDummyCols(df.clone, use_all_factor_levels)
}
