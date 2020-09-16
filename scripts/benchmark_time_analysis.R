# execution
getData <- function(algo, begin.date) {
    algo.file <- algo
    algo.testcases <- algo
    algo_parts <- strsplit(algo, "-")[[1]]
    backend <- NULL
    if (length(algo_parts) > 1) {
      if (algo_parts[2] == "client") {
        algo.testcases <- algo_parts[1]
      } else {
        algo.file <- algo_parts[1]
      }
    } else {
      algo_parts <- strsplit(algo, "_")[[1]]
      if (length(algo_parts) > 1) {
        algo.file <- algo_parts[1]
        backend <- algo_parts[2]
        if (backend == "gpu") {
          algo.testcases <- algo
        } else {
          algo.testcases <- algo_parts[1]
        }
        algo <- algo_parts[1]
      } else {
        algo.testcases <- algo
      }
    }
    target <- "train_time"
    tests <- read.csv(paste0(dir, "jenkins/test_cases_", algo.testcases, ".csv"))
    data <- read.csv(paste0(dir, "dash/.cache_test.0xdata.com_benchmarks_master_", algo.file, ".csv"))
    data$date <- as.Date(as.character(data$date))
    data <- data[ data$date >= begin.date, ]
    
    result <- data.frame(
      algo=algo,
      test_case_id = tests$test_case_id,
      dataset = tests$dataset
    )
    if ("ntrees" %in% names(tests)) {
      result[["ntrees"]] <- tests$ntrees
    } else if ("solver" %in% names(tests)) {
      result[["solver"]] <- tests$solver
    } else if ("numRows" %in% names(tests)) {
      result[["numRows"]] <- tests$numRows
      result[["numCols"]] <- tests$numCols
    } else if ("model_type" %in% names(tests)) {
        result[["model_type"]]] <- tests$model_type
        result[["min_rule_length"]] <- tests$min_rule_length
        result[["max_rule_length"]] <- tests$max_rule_length
    } else {
      stop("neither tree, solver, size or model_type")
    }
    if ("backend" %in% names(tests)) {
      result[["backend"]] <- tests$backend
    }
    if ("categorical_encoding" %in% names(tests)) {
      result[["categorical_encoding"]] <- tests$categorical_encoding
    }
    
    for (col in c("abs.min", "abs.max", "norm.min", "norm.max", "min", "max")) {
      result[[col]] <- 0
    }
    
    algo_data <- data[data$algorithm == algo, ]
    if (!is.null(backend)) {
      algo_data <- algo_data[algo_data$backend == backend, ]
    }
    for (i in 1:nrow(result)) {
      test_case_id <- tests$test_case_id[i]
      test_data <- algo_data[ algo_data$test_case_id == test_case_id, ]
      quants <- quantile(test_data[[target]], probs = c(0.05, 0.95))
      abs.min <- floor(min(test_data[[target]]))
      abs.max <- ceiling(max(test_data[[target]]))
      test_data <- test_data[which(test_data[[target]] >= quants[[1]] & test_data[[target]] <= quants[[2]]), ]
      result$abs.min[i] <- abs.min
      result$abs.max[i] <- abs.max
      result$norm.min[i] <- floor(min(test_data[[target]]))
      result$norm.max[i] <- ceiling(max(test_data[[target]]))
      if (abs(result$abs.min[i] - result$norm.min[i]) > 0.2*result$norm.min[i]) {
        result$min[i] <- result$norm.min[i]
      } else {
        result$min[i] <- result$abs.min[i]
      }
      if (abs(result$abs.max[i] - result$norm.max[i]) > 0.2*result$norm.max[i]) {
        result$max[i] <- result$norm.max[i]
      } else {
        result$max[i] <- result$abs.max[i]
      }
    }
    if ("ntrees" %in% names(result)) {
      result <- result[order(result$dataset, result$ntrees), ]
    } else {
      result <- result[order(result$dataset), ]
    }
    return(result)
}

# configuration
dir <- "../ml-benchmark/"
begin.date <- seq(Sys.Date(), length = 2, by = "-3 months")[2]
tests <- c(
    "gbm",
    "gbm-client",
    "glm",
    "xgb",
    "xgb_gpu",
    "xgb-vanilla",
    "xgb-dmlc",
    "gbm",
    "sort",
    "merge",
    "rulefit"
)

for (t in tests) {
    View(getData(t, begin.date), t)
}
