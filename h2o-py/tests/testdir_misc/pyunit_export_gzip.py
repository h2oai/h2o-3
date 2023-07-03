import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from os import path
import binascii

'''
Export file with h2o.export_file compressed with 'gzip'
'''


def is_gzip_file(path):
    with open(path, 'rb') as f:
        magic = binascii.hexlify(f.read(2))
        return magic == b'1f8b'


def export_gzip():
    prostate = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    target = path.join(pyunit_utils.locate("results"), "prostate_export.csv.gzip")
    h2o.export_file(prostate, target, compression="gzip")

    assert is_gzip_file(target)

    prostate_gzip = h2o.import_file(target)

    assert pyunit_utils.compare_frames(prostate, prostate_gzip, numElements=2)


if __name__ == "__main__":
    pyunit_utils.standalone_test(export_gzip)
else:
    export_gzip()



