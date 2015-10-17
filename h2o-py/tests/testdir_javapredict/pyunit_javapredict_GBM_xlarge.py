import sys
sys.path.insert(1, "../../")
import h2o, tests

def javapredict_gbm_xlarge():

    hdfs_name_node = tests.hadoop_namenode()
    hdfs_file_name = "/datasets/z_repro.csv.gz"
    url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_file_name)

    params = {'ntrees':22, 'max_depth':37, 'min_rows':1, 'sample_rate':0.1} # 651MB pojo
    print "Parameter list:"
    for k,v in zip(params.keys(), params.values()): print "{0}, {1}".format(k,v)

    train =  h2o.import_file(url)
    test = train[range(0,10),:]
    x = range(1,train.ncol)
    y = 0

    tests.javapredict("gbm", "numeric", train, test, x, y, **params)

if __name__ == "__main__":
    tests.run_test(sys.argv, javapredict_gbm_xlarge)