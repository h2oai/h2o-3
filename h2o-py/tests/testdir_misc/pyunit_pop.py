import sys
sys.path.insert(1, "../../")
import h2o, tests

def pyunit_pop(ip,port):

  pros = h2o.import_file(h2o.locate("smalldata/prostate/prostate.csv"))
  nc = pros.ncol
  popped_col = pros.pop(pros.names[0])

  print pros.dim
  print popped_col.dim

  assert popped_col.ncol==1
  assert pros.ncol==nc-1

if __name__ == "__main__":
  tests.run_test(sys.argv, pyunit_pop)
