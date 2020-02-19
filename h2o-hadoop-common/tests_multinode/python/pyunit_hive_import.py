#! /usr/env/python

import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o

def adapt_frame(dataset, column_prefix=""):
    dataset[column_prefix + "community_area_name"] = dataset[column_prefix + "community_area_name"].asfactor()
    return dataset

def hive_import():
    hdfs_name_node = pyunit_utils.hadoop_namenode()
    hive_host = os.getenv("HIVE_HOST")
    connection_url = "jdbc:hive2://{0}:10000/default".format(hive_host)
    krb_enabled = os.getenv('KRB_ENABLED', 'false').lower() == 'true'
    if krb_enabled:
        connection_url += ";auth=delegationToken"
        
    # read original
    file_url = "hdfs://{0}{1}".format(hdfs_name_node, "/user/jenkins/smalldata/chicago/chicagoCensus.csv")
    dataset_original = h2o.import_file(file_url)

    # read TABLE from Hive JDBC
    table_jdbc = h2o.import_sql_table(connection_url, "chicago", "", "", fetch_mode="SINGLE")
    table_jdbc = adapt_frame(table_jdbc, column_prefix="chicago.")
    pyunit_utils.compare_frames_local(dataset_original, table_jdbc, prob=1)

    # read TABLE from Hive FS
    table_direct = h2o.import_hive_table(connection_url, "chicago")
    table_direct = adapt_frame(table_direct)
    pyunit_utils.compare_frames_local(dataset_original, table_direct, prob=1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(hive_import)
else:
    hive_import()
