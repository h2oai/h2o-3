from value_space import Value

class FeatureSpaceSample():

  test_case_id_counter = 1

  def __init__(self, name, points):
    """
    A sampling of points in a FeatureSpace.

    :param name: the name of the FeatureSpace. (string)
    :param points: A point is a combination of all the feature's dimensions assigned a concrete value. `points` is a
                   list of these. (list)
    """
    self.name = name
    self.points = points

  def make_R_tests(self, f):
    """
    # R feature test case schema:
    # id|feature|feature_params|data_set_ids|validation_method|validation_data_set_id|description

    :return: list of tests.
    """

    tests = []
    for point in self.points:
      feature_params = []
      data_set_ids = []
      for name, dim in zip(point.keys(), point.values()):
        if isinstance(dim, Value) or dim is None: # make feature_params
          # turn value into a valid R expression
          if   dim is None or dim.value is None:          value = "NULL"
          elif dim.value_type == "logical":               value = "TRUE" if dim.value else "FALSE"
          elif dim.value_type == "string":                value = "'{0}'".format(dim.value)
          elif dim.value_type in ["integer[]", "real[]"]: value = 'c(' + ','.join(str(v) for v in dim.value) + ')'
          elif dim.value_type in ["enum[]", "string[]"]:  value = 'c(' + ','.join("'"+v+"'".format(v) for v in
                                                                                  dim.value) + ')'
          else:                                           value = str(dim.value)
          feature_params.append(name + "=" + value)
        else: # make test case data_set_ids
          data_set_ids.append(name + "=" + str(dim.id))

      feature_params_string = ';'.join(feature_params)
      data_set_ids_string = ';'.join(data_set_ids)

      # make description
      description = "{0} feature test case.".format(self.name)

      tests.append([FeatureSpaceSample.test_case_id_counter, self.name, feature_params_string, data_set_ids_string,
                    "R", "", description])
      FeatureSpaceSample.test_case_id_counter += 1

    for test in tests: f.write('~'.join([str(field) for field in test]) + '\n')