import sys
sys.path.insert(1, "../../")
import h2o, tests

def directory_import():

    hadoop_namenode_is_accessible = tests.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = tests.hadoop_namenode()
        url1 = "hdfs://{0}{1}".format(hdfs_name_node, "/datasets/iris/identical_iris_files/iris1.csv")
        url2 = "hdfs://{0}{1}".format(hdfs_name_node, "/datasets/iris/identical_iris_files/")
        print "Importing HDFS file {0} and directory {1}".format(url1, url2)
        frm_one = h2o.import_file(url1)
        frm_all = h2o.import_file(url2)

        r1, c1 = frm_one.dim
        ra, ca = frm_all.dim

        assert r1*3 == ra, "Expected 3 times the rows, but got {0} and {1}".format(r1,ra)
        assert c1 == ca, "Expected same number of cols, but got {0} and {1}".format(c1,ca)

    small1 = tests.locate("smalldata/jira/identical_files/iris1.csv")
    small2 = small1.split("iris1.csv")[0]
    print "Importing smalldata file {0} and directory {1}".format(small1, small2)
    frm_one = h2o.import_file(small1)
    frm_all = h2o.import_file(small2)

    r1, c1 = frm_one.dim
    ra, ca = frm_all.dim

    assert r1*3 == ra, "Expected 3 times the rows, but got {0} and {1}".format(r1,ra)
    assert c1 == ca, "Expected same number of cols, but got {0} and {1}".format(c1,ca)

if __name__ == "__main__":
    tests.run_test(sys.argv, directory_import)
