get_workdir <- function() {
    return(Sys.getenv("HDFS_WORKSPACE")) 
}

get_script_path <- function(env_var) {
    return(paste0(Sys.getenv("H2O_HOME"), "/", Sys.getenv(env_var)))
}

start_cluster <- function(name) {
    script <- get_script_path("H2O_START_SCRIPT")
    notify_file <- paste0("notify_", name)
    driver_log_file <- paste0("driver_", name, ".log")
    clouding_dir <- paste0(get_workdir(), "_clouding_", name)
    job_name <- paste0(Sys.getenv("H2O_JOB_NAME"), "_", name)
    args <- c(
        "--cluster-name", name,
        "--clouding-dir", clouding_dir,
        "--notify-file", notify_file,
        "--driver-log-file", driver_log_file,
        "--hadoop-version", Sys.getenv("H2O_HADOOP"),
        "--job-name", job_name,
        "--nodes", "3", "--xmx", "8G",
        "--disown"
    )
    notify_file_path <- paste0(Sys.getenv("H2O_HOME"), "/", notify_file)
    if (file.exists(notify_file_path)) {
        file.remove(notify_file_path)
    }
    run_script(script, args)
    con <- file(notify_file_path, "r")
    cluster_url <- readLines(con, n = 1)
    close(con)
    cluster_url <- sub("\\s+$", "", cluster_url)
    return("http://" + cluster_url) 
}


stop_cluster <- function(name) {
    script <- get_script_path("H2O_KILL_SCRIPT")
    notify_file <- paste0("notify_", name)
    driver_log_file <- paste0("driver_", name, ".log")
    yarn_logs_file <- paste0("yarn_", name, ".log")
    args <- c(
        "--notify-file", notify_file,
        "--driver-log-file", driver_log_file,
        "--yarn-logs-file", yarn_logs_file
    )
    run_script(script, args)
}


run_script <- function(script, args) {
    tryCatch({
        setwd(os.getenv("H2O_HOME"))
        result <- system2(
            script, args, stdout=TRUE, stderr=TRUE
        )
        print(paste(args[0], "script output:"))
        print("--------------------")
        print(result)
        print("--------------------")
        if (result$status != 0) {
            stop(paste("Script", script, "failed, with exit status ", result$status))
        }
    })
}
