from __future__ import print_function
import sys
sys.path.insert(1, "../../")
import h2o
import csv
import os
import tempfile
from tests import pyunit_utils

def csv_header_test():
    fieldnames = []
    for idx in range(1000):
        # Resulting CSV file is going to have large header and data itself will take only a small fraction of the file
        fieldnames.append("VeryLongFieldName_VeryLongFieldName_VeryLongFieldName_VeryLongFieldName_VeryLongFieldName"
                          "_VeryLongFieldName_VeryLongFieldName_VeryLongFieldName_" + str(idx))
    fd, temp_filename = tempfile.mkstemp()
    try:
        with open(temp_filename, 'w') as file_:
            writer = csv.DictWriter(file_, fieldnames=fieldnames)
            writer.writeheader()
            # write 100 lines, resulting chunk size should be very small
            for _ in range(10):
                writer.writerow({k: 'Y' for k in fieldnames})
        h2oframe = h2o.import_file(temp_filename, header=1)
        pandas_df = h2oframe.as_data_frame()
        first_line = pandas_df.iloc[0]
        assert not any(first_line[k] == k for k in fieldnames)
    finally:
        os.remove(temp_filename)
if __name__ == "__main__":
    pyunit_utils.standalone_test(csv_header_test)
else:
    csv_header_test()
