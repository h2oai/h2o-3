


rtest <- function() {

    hdfs_name_node = hadoop.namenode()
    hdfs_data_file = "/datasets/runit/covtype.data"
    hdfs_tmp_dir = "/tmp/runit"

    url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
    model_path <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_tmp_dir)
    data.hex <- h2o.importFile(url)

    data.hex[,55] <- ifelse(data.hex[,55] == 1, 1, 0)
    print(summary(data.hex))

    covtype.glm <- h2o.glm(x = setdiff(1:54, c(21,29)), y = 55, training_frame = data.hex, family = "gaussian", alpha = 0, lambda = 0)
    covtype.glm

}

doTest("Test",rtest)

