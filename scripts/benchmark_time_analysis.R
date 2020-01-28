# configuration
dir <- "/path/to/ml-benchmark/"
algo <- "xgb_gpu"
# execution
backend <- NULL
algo.file <- algo
algo.testcases <- algo
algo_parts = strsplit(algo, "-")[[1]]
if (length(algo_parts) > 1) {
  algo.file <- algo_parts[1]
  if (algo_parts[1] == "client") {
    algo.testcases <- algo_parts[1]
  }
}
algo_parts = strsplit(algo, "_")[[1]]
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

target = "train_time"
tests <- read.csv(paste0(dir, "jenkins/test_cases_", algo.testcases, ".csv"))
data <- read.csv(paste0(dir, "dash/.cache_test.0xdata.com_benchmarks_master_", algo.file, ".csv"))
data$date <- as.Date(as.character(data$date))
data <- data[ data$date >= as.Date("2019-05-01"), ]

result <- data.frame(
  test_case_id = tests$test_case_id,
  dataset = tests$dataset
)
if ("ntrees" %in% names(tests)) {
  result[["ntrees"]] <- tests$ntrees
} else if ("solver" %in% names(tests)) {
  result[["solver"]] <- tests$solver
} else {
  stop("neitehr tree nor solver")
}
if ("backend" %in% names(tests)) {
  result[["backend"]] <- tests$backend
}

for (col in c("abs.min", "abs.max", "min.2sd", "max.2sd", "min", "max")) {
  result[[col]] <- 0
}

algo_data <- data[data$algorithm == algo, ]
if (!is.null(backend)) {
  algo_data <- algo_data[algo_data$backend == backend, ]
}
for (i in 1:nrow(result)) {
  test_case_id <- tests$test_case_id[i]
  test_data <- algo_data[ algo_data$test_case_id == test_case_id, ]
  sd.time <- sd(test_data[[target]])
  mean.time <- mean(test_data[[target]])
  result$abs.min[i] <- floor(min(test_data[[target]]))
  result$abs.max[i] <- ceiling(max(test_data[[target]]))
  result$min.2sd[i] <- floor(mean.time - 2 * sd.time)
  result$max.2sd[i] <- ceiling(mean.time + 2 * sd.time)

  result$min[i] <- max(c(result$abs.min[i], result$min.2sd[i]))
  result$max[i] <- min(c(result$abs.max[i], result$max.2sd[i]))
}
View(result)
