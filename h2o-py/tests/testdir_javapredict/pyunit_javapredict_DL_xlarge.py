import sys
sys.path.insert(1, "../../")
import h2o, tests

def javapredict_dl_xlarge():

    hdfs_name_node = tests.hadoop_namenode()
    hdfs_file_name = "/datasets/z_repro.csv"
    url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_file_name)

    params = {'hidden':[3500, 3500], 'epochs':0.0001} # 436MB pojo
    print "Parameter list:"
    for k,v in zip(params.keys(), params.values()): print "{0}, {1}".format(k,v)

    train =  h2o.import_file(url)
    test = train[range(0,10),:]
    x = range(1,train.ncol)
    y = 0

    tests.javapredict("deeplearning", "numeric", train, test, x, y, **params)

if __name__ == "__main__":
    tests.run_test(sys.argv, javapredict_dl_xlarge)