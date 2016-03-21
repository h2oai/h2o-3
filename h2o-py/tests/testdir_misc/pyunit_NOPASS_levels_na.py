import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def levels_na():

  cov_type = h2o.import_file(pyunit_utils.locate("bigdata/laptop/covtype/covtype.full.csv"))
  assert cov_type.levels() == [[], [], [], [], [], [], [], [], [], [], ['area_0', 'area_1', 'area_2', 'area_3'], ['type_0', 'type_1', 'type_10', 'type_11', 'type_12', 'type_13', 'type_14', 'type_15', 'type_16', 'type_17', 'type_18', 'type_19', 'type_2', 'type_20', 'type_21', 'type_22', 'type_23', 'type_24', 'type_25', 'type_26', 'type_27', 'type_28', 'type_29', 'type_3', 'type_30', 'type_31', 'type_32', 'type_33', 'type_34', 'type_35', 'type_36', 'type_37', 'type_38', 'type_39', 'type_4', 'type_5', 'type_6', 'type_7', 'type_8', 'type_9'], ['class_1', 'class_2', 'class_3', 'class_4', 'class_5', 'class_6', 'class_7']]
  assert cov_type.nlevels() == [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 40, 7]

if __name__ == "__main__":
  pyunit_utils.standalone_test(levels_na)
else:
  levels_na()
