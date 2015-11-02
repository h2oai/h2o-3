test <-
  function() {
    
    running_inside_hexdata = file.exists("/mnt/0xcustomer-datasets/c4/test20130806.tsv")
    
    if (!running_inside_hexdata) {
      # hdp2.2 cluster
      stop("0xdata internal test and data.")
    }
    
    data.hex <- h2o.uploadFile("/mnt/0xcustomer-datasets/c4/test20130806.tsv",destination_frame = "regs",header = T)
    
    num_rows = nrow(data.hex)
    expect_equal(num_rows,550311)
  }

doTest("Test", test)