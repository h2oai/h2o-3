################################################################################
##
## Verifying that Python can define features as categorical or continuous on import
##
################################################################################
import sys, os
sys.path.insert(1, "../../")
import h2o

def continuous_or_categorical(ip, port):
  df_hex = h2o.import_frame(h2o.locate("smalldata/jira/hexdev_29.csv"),
    )# TODO: SOME FORMAT HERE

  assert (df_hex['h1'].isfactor())
  assert (df_hex['h2'].isfactor())
  assert (df_hex['h3'].isfactor())

if __name__ == "__main__":
    h2o.run_test(sys.argv, continuous_or_categorical)
