import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from os import path
import struct

'''
Export file with h2o.export_file compressed with 'zstd'
'''


def is_zstd_file(path):
    with open(path, 'rb') as f:
        magic_bytes = f.read(4)
        return struct.unpack('<I', magic_bytes)[0] == 0xFD2FB528


def export_zstd():
    prostate = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    target = path.join(pyunit_utils.locate("results"), "prostate_export.csv.zst")
    h2o.export_file(prostate, target, compression="zstd")

    assert is_zstd_file(target)

    prostate_zstd = h2o.import_file(target)

    assert pyunit_utils.compare_frames(prostate, prostate_zstd, numElements=2)


if __name__ == "__main__":
    pyunit_utils.standalone_test(export_zstd)
else:
    export_zstd()



