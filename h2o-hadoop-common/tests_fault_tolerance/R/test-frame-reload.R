source("fault_tolerance_utils.R")

name_node <- Sys.getenv("NAME_NODE")
work_dir <- get_workdir()
dataset <- "datasets/mnist/train.csv.gz"

test_that("Can reload a Frame from hdfs", {
    print("Import and save...")
    tryCatch({
        cluster_1 <- start_cluster("saver")
        h2o.connect(url=cluster_1)
        df_orig <- h2o.import_file(path=sprintf("hdfs://%s%s", name_node, dataset))
        df_key <- h2o.getId(df_orig)
        df_orig_r <- as.data.frame(df_orig)
        h2o.save_frame(df_orig, work_dir)
    }, finally={
        stop_cluster("saver")
    })

    print("Load saved...")
    tryCatch({
        cluster_2 <- start_cluster("loader")
        h2o.connect(url=cluster_2)
        df_loaded <- h2o.load_frame(df_key, work_dir)
        h2o.save_frame(df_orig, work_dir)
        df_loaded_r <- as.data.frame(df_loaded)
    }, finally={
        stop_cluster("loader")
    })

    print("Comparing frames...")
    # just a basic comparison, more testing done in py
    expect_true(all(dim(df_orig_r) == dim(df_loaded_r)))

    print("Test passed.")
})
