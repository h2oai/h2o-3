from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import os
import tempfile
from tests import pyunit_utils


def generate_large_file(path, size):
    with open(path, "wb") as f:
        f.seek(size-1)
        f.write(b"\0")
    assert size == os.stat(path).st_size


def upload_large_file():
    path = os.path.join(tempfile.mkdtemp(), "large.bin") 
    byte_size = 2 * 1024 * 1024 * 1024 + 1  # 2GB + 1 byte
    generate_large_file(path, byte_size)
    raw_data = h2o.api("POST /3/PostFile", filename=path)
    print(raw_data)
    assert raw_data["total_bytes"] == byte_size
    h2o.remove(raw_data["destination_frame"])


if __name__ == "__main__":
    pyunit_utils.standalone_test(upload_large_file)
else:
    upload_large_file()

