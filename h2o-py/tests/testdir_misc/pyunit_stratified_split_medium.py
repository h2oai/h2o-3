import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils





def stratified_split():
  fr = h2o.import_file(pyunit_utils.locate("bigdata/laptop/covtype/covtype.data"))
  stratified = fr[54].stratified_split()
  train = fr[stratified=="train"]
  test  = fr[stratified=="test"]
  print (fr[54].table()["Count"] / fr[54].table()["Count"].sum()).show()
  print (train[54].table()["Count"] / train[54].table()["Count"].sum()).show()
  print (test[54].table()["Count"] / test[54].table()["Count"].sum()).show()



if __name__ == "__main__":
  pyunit_utils.standalone_test(stratified_split)
else:
  stratified_split()
