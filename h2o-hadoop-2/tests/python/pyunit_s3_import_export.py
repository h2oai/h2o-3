#! /usr/env/python

import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
from tests import pyunit_utils
from datetime import datetime
import h2o
import uuid
from pandas.util.testing import assert_frame_equal
import boto3

def get_supported_filesystems():
    spec = os.getenv('HADOOP_S3_FILESYSTEMS', 's3n,s3a')
    return spec.split(",") if spec else None

def s3_import_export():
    supported_filesystems = get_supported_filesystems()
    if not supported_filesystems:
        print("Test skipped - this build doesn't support any Hadoop S3 filesystem implementations")
        return
    local_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    for scheme in supported_filesystems:
        timestamp = datetime.today().utcnow().strftime("%Y%m%d-%H%M%S.%f")
        unique_suffix = str(uuid.uuid4())
        s3_path = scheme + "://test.0xdata.com/h2o-hadoop-tests/test-export/" + scheme + "/exported." + \
                  timestamp + "." + unique_suffix + ".csv.zip"
        h2o.export_file(local_frame, s3_path)

        s3 = boto3.resource('s3')
        client = boto3.client('s3')
        # S3 might have a delay in indexing the file (usually milliseconds or hundreds of milliseconds)
        # Wait for the file to be available, if not available in the biginning, try every 2 seconds, up to 10 times
        client.get_waiter('object_exists').wait(Bucket='test.0xdata.com',
                                                Key="h2o-hadoop-tests/test-export/" + scheme + "/exported." + \
                                                    timestamp + "." + unique_suffix + ".csv.zip",
                                                WaiterConfig={
                                                    'Delay': 2,
                                                    'MaxAttempts': 10
                                                })
        s3_frame = h2o.import_file(s3_path)
        assert_frame_equal(local_frame.as_data_frame(), s3_frame.as_data_frame())
        
        s3.Object(bucket_name='test.0xdata.com', key="h2o-hadoop-tests/test-export/" + scheme + "/exported." + \
                                                     timestamp + "." + unique_suffix + ".csv.zip").delete()

if __name__ == "__main__":
    pyunit_utils.standalone_test(s3_import_export)
else:
    s3_import_export()
