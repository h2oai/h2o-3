source("fault_tolerance_utils.R")

name_node <- Sys.getenv("NAME_NODE")
work_dir <- sprintf("hdfs://%s%s", name_node, get_workdir())
dataset <- "/datasets/mnist/train.csv.gz"

test_that("Can reload a Frame from hdfs", {
    print("Import and save...")
    saver_cluster_name <- "saver-r"
    tryCatch({
        cluster_1 <- start_cluster(saver_cluster_name)
        h2o.connect(ip=cluster_1[1], port=as.numeric(cluster_1[2]))
        df_orig <- h2o.importFile(path=sprintf("hdfs://%s%s", name_node, dataset))
        df_key <- h2o.getId(df_orig)
        df_orig_r <- as.data.frame(df_orig)
        h2o.save_frame(df_orig, work_dir)
    }, finally={
        stop_cluster(saver_cluster_name)
    })

    print("Load saved...")
    loader_cluster_name <- "loader-r"
    tryCatch({
        cluster_2 <- start_cluster(loader_cluster_name)
        h2o.connect(ip=cluster_2[1], port=as.numeric(cluster_2[2]))
        df_loaded <- h2o.load_frame(df_key, work_dir)
        df_loaded_r <- as.data.frame(df_loaded)
    }, finally={
        stop_cluster(loader_cluster_name)
    })

    print("Comparing frames...")
    # just a basic comparison, more testing done in py
    expect_true(all(dim(df_orig_r) == dim(df_loaded_r)))

    print("Test passed.")
})
