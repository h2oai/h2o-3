################################################################################
##
## Verifying that Python can define features as categorical or continuous on import
##
################################################################################
import sys, os
sys.path.insert(1, "../../")
import h2o, tests

def continuous_or_categorical():
  fraw = h2o.lazy_import(tests.locate("smalldata/jira/hexdev_29.csv"))
  fsetup = h2o.parse_setup(fraw)
  fsetup["column_types"][0] = "ENUM"
  fsetup["column_types"][1] = "ENUM"
  fsetup["column_types"][2] = "ENUM"

  df_hex = h2o.parse_raw(fsetup)

  df_hex.summary()

  assert (df_hex['h1'].isfactor())
  assert (df_hex['h2'].isfactor())
  assert (df_hex['h3'].isfactor())

if __name__ == "__main__":
    tests.run_test(sys.argv, continuous_or_categorical)
