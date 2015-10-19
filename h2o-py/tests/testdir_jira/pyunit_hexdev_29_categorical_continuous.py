################################################################################
##
## Verifying that Python can define features as categorical or continuous
##
################################################################################
import h2o, tests

def continuous_or_categorical():
  # connect to h2o
  

  aa = {
    'h1': [1, 8, 4, 3, 6],
    'h2': ["fish", "cat", "fish", "dog", "bird"],
    'h3': [0, 1, 0, 0, 1]
  }

  df_hex = h2o.H2OFrame(python_obj = aa)

  df_hex.show()
  df_hex.summary()

  assert (not df_hex['h1'].isfactor())
  assert (df_hex['h2'].isfactor())
  assert (not df_hex['h3'].isfactor())

  df_hex['h1'] = df_hex['h1'].asfactor()
  df_hex['h2'] = df_hex['h2'].asfactor()
  df_hex['h3'] = df_hex['h3'].asfactor()

  df_hex.show()
  df_hex.summary()

  assert (df_hex['h1'].isfactor())
  assert (df_hex['h2'].isfactor())
  assert (df_hex['h3'].isfactor())

  df_hex['h1'] = df_hex['h1'].asnumeric()
  df_hex['h2'] = df_hex['h2'].asnumeric()
  df_hex['h3'] = df_hex['h3'].asnumeric()

  df_hex.show()
  df_hex.summary()

  assert (not df_hex['h1'].isfactor())
  assert (not df_hex['h2'].isfactor())
  assert (not df_hex['h3'].isfactor())


pyunit_test = continuous_or_categorical
