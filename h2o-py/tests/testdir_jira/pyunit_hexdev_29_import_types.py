################################################################################
##
## Verifying that Python can define features as categorical or continuous on import
##
################################################################################
import h2o, tests

def continuous_or_categorical():
  df_hex = h2o.import_file(tests.locate("smalldata/jira/hexdev_29.csv"), col_types=["enum"]*3)

  df_hex.summary()

  assert (df_hex['h1'].isfactor())
  assert (df_hex['h2'].isfactor())
  assert (df_hex['h3'].isfactor())


pyunit_test = continuous_or_categorical
