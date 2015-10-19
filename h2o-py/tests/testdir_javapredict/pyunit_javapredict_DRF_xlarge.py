



def javapredict_drf_xlarge():

    hdfs_name_node = pyunit_utils.hadoop_namenode()
    hdfs_file_name = "/datasets/z_repro.csv"
    url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_file_name)

    params = {'ntrees':20, 'max_depth':35, 'min_rows':1} # 739MB pojo
    print "Parameter list:"
    for k,v in zip(params.keys(), params.values()): print "{0}, {1}".format(k,v)

    train =  h2o.import_file(url)
    test = train[range(0,10),:]
    x = range(1,train.ncol)
    y = 0

    pyunit_utils.javapredict("random_forest", "numeric", train, test, x, y, **params)


javapredict_drf_xlarge()
