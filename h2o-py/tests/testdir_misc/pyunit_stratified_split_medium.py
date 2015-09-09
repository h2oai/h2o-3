
import sys
sys.path.insert(1, "../../")
import h2o,tests

def stratified_split():
  fr = h2o.import_file("bigdata/laptop/covtype")
  stratified = fr[54].stratified_split()
  train = fr[stratified=="train"]
  test  = fr[stratified=="test"]
  print (fr[54].table()["Count"] / fr[54].table()["Count"].sum()).show()
  print (train[54].table()["Count"] / train[54].table()["Count"].sum()).show()
  print (test[54].table()["Count"] / test[54].table()["Count"].sum()).show()

if __name__ == "__main__":
  tests.run_test(sys.argv, stratified_split)
