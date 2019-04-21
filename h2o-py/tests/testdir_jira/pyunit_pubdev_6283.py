import h2o
from tests import pyunit_utils


def pubdev_6283():

    data = h2o.import_file(pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    data['C3'] = data['C3'].asfactor()
    stratified = data[54].stratified_split()
    train = data[stratified=="train"]
    test  = data[stratified=="test"]
    assert train.nrows + test.nrows == data.nrows
    
    split=data['C3'].stratified_split(test_frac=0.3)
    train=data[split=='train']
    test=data[split=='test']
    assert train.nrows + test.nrows == data.nrows
    
    split = train['C3'].stratified_split(test_frac=0.1)
    split.show()


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_6283)
else:
    pubdev_6283()
