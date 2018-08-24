from __future__ import print_function
import sys

sys.path.insert(1, "../../")
import h2o
import csv
import random
import os
import tempfile
from tests import pyunit_utils

#
def csv_header_test():
    components = []
    for idx in range(704):
        components.append('Field{idx}')

    # 1000 columns
    keys = list(set('_'.join(random.sample(components, 5)) for _ in range(1000)))

    fieldnames = keys[:idx]
    fd, temp_filename = tempfile.mkstemp()
    try:
        with open(temp_filename, 'w') as file_:
            writer = csv.DictWriter(file_, fieldnames=fieldnames)
            writer.writeheader()

            # write 100 lines, resulting chunk size should be very small
            for _ in range(100):
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
