import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def pyunit_apply():
  fr = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))

  fr.apply(lambda x: x["PSA"], axis=1).show()
  print
  print
  fr.apply(lambda x: x['PSA'] > x['VOL'],axis=1).show()
  print
  print fr.mean()
  fr.apply(lambda x: x.mean()).show()



  fr.apply(lambda col: col.abs()).show()
  fr.apply(lambda col: col.cos()).show()
  fr.apply(lambda col: col.sin()).show()
  fr.apply(lambda col: col.ceil()).show()
  fr.apply(lambda col: col.floor()).show()
  fr.apply(lambda col: col.cosh()).show()
  fr.apply(lambda col: col.exp()).show()
  fr.apply(lambda col: col.log()).show()
  fr.apply(lambda col: col.sqrt()).show()
  fr.apply(lambda col: col.tan()).show()
  fr.apply(lambda col: col.tanh()).show()

  fr.apply(lambda col: (col*col - col*5*col).abs() - 55/col ).show()



if __name__ == "__main__":
  pyunit_utils.standalone_test(pyunit_apply)
else:
  pyunit_apply()