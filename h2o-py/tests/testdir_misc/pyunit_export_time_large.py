from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import string
import random
import time
'''
The below function will test if the exporting of a file in H2O is NOT asynchronous. Meaning the h2o.export_file()
waits for the entire process to finish before exiting.
'''
def export_time():
    pros_hex = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/citibike-nyc/2013-07.csv"))

    def id_generator(size=6, chars=string.ascii_uppercase + string.digits):
        return ''.join(random.choice(chars) for _ in range(size))

    fname = id_generator() + "_prediction.csv"

    path = pyunit_utils.locate("results")
    dname = path + "/" + fname

    start = time.time()
    h2o.export_file(pros_hex,dname)
    end = time.time()
    time_to_export = end-start
    print("Time to export is",time_to_export,"seconds.")
    assert time_to_export > 2, "File export happened instantly (less than 2 seconds). Please check if h2o.export() properly exported file"

if __name__ == "__main__":
    pyunit_utils.standalone_test(export_time)
else:
    export_time()



