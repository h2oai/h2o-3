import random
import string
import os
import csv

class Value:
  def __init__(self, value_type, value):
    """
    This class is used to represent an element in the domain of some value space. The allowable value spaces are the
    ScalerValueSpace, ArrayValueSpace, and the DatasetValueSpace. See these classes for further details.

    :param value_type: the type of the element. the allowable types are "integer", "real", "string", "enum", "logical",
                       "integer[]", "real[]", "string[]", "enum[]", "logical[]", "dataset", and "null". (string)
    :param value: they python representation of the element.
    """

    self.value_type = value_type
    self.value = value

class NullValueSpace():
  def __init__(self, space_type=None): self.space_type = space_type
  def sample(self): return [Value(value_type="null", value=None)]

class ScalerValueSpace():
  def __init__(self, space_type, set=None, lower=None, upper=None):
    """
    This class is used to represent an entire domain, or set, of scaler Values. All Values in the domain must be of the
    same `space_type`. Allowable `space_types` are "integer", "real", "string", "enum", and "logical", which
    correspond to the Values' `value_type`. Either `set` OR (`lower` AND `upper`) can be specified, but not both.

    :param space_type: the Value.value_type of all elements in the space. (string)
    :param set: set of allowable Values. (list of Values)
    :param lower: for "integer" and "real" types this is the lower (upper) bound on the domain of values. for "string"
                  types this is fewest (greatest) number of allowable characters in the string value. this parameter is
                  undefined for "enum" and "logical" types. (integer/float)
    :param upper: see lower. (integer/float)
    """

    self.space_type = space_type
    self.set = set
    self.lower = lower
    self.upper = upper

  def sample(self, size=1, all=False):
    """
    Pick `size` elements randomly (with replacement) from `self.set` or from the range specified by `self.lower` and
    `self.upper`

    :param all: return `self.set`. ignored if `self.set` is None. (logical)
    :param size: the number of samples to return. if `self.set` is specified and all is True, `size` is ignored.
                 (integer)
    :return: a list of Values from the ScalerValueSpace.
    """

    if self.set is None:
      if self.space_type == "integer" or self.space_type == "real":
        vals = [random.uniform(self.lower, self.upper) for _ in range(size)]
        if self.space_type == "integer": vals = [int(val) for val in vals]
        return [Value(value_type=self.space_type, value=val) for val in vals]
      elif self.space_type == "string":
        vals = [''.join(random.choice(string.ascii_uppercase + string.digits + string.ascii_lowercase) for _ in
                        range(random.choice(range(self.lower, self.upper+1)))) for _ in range(size)]
        return [Value(value_type=self.space_type, value=val) for val in vals]
      else: raise ValueError("ScalerValueSpace.sample() is not defined for space_type {0} when self.set is "
                             "None".format(self.space_type))
    else:
      if all: return self.set
      else:   return [random.choice(self.set) for _ in range(size)]

class ArrayValueSpace:
  def __init__(self, space_type, set=None, element_value_space=None, exact_array_size=None, lower_array_size=None,
               upper_array_size=None, sort=False):
    """
    This class is used to represent an entire domain, or set, of array Values. All Values in the domain must be of the
    same `space_type`. Allowable `space_types` are "integer[]", "real[]", "string[]", "enum[]", and "logical[]", which
    correspond to the Values' `value_type`. If `set` is specified, then `element_value_space`, `exact_array_size`,
    `lower_array_size`, and `upper_array_size` are ignored.

    :param space_type: the Value.value_type of all elements in the space. (string)
    :param set: set of allowable Values. (list of Values)
    :param element_value_space: the ScalerValueSpace for the elements of the respective array. (ScalerValueSpace)
    :param lower_array_size: minimum allowable size of an array. (integer)
    :param upper_array_size: maximum allowable size of an array. (integer)
    :param exact_array_size: exact size of arrays in the ArrayValueSpace. (integer)
    """

    self.space_type = space_type
    self.set = set
    self.element_value_space = element_value_space
    self.lower_array_size = lower_array_size
    self.upper_array_size = upper_array_size
    self.exact_array_size = exact_array_size
    self.sort = sort

  def sample(self, size=1, all=False):
    """
    Pick `size` elements randomly (with replacement) from `self.set` or from the range specified by the
    `element_value_space`, `lower_array_size`, `upper_array_size`, or `exact_array_size`

    :param all: return self.set`. ignored if `self.set` is None. (logical)
    :param size: the number of samples to return. if `self.set` is specified and all is True, `size` is ignored.
                 (integer)
    :return: a list of Values from the ArrayValueSpace.
    """

    if self.set is None:
      if self.exact_array_size is None:
        return [Value(value_type=self.space_type,
                      value=[self.element_value_space.sample()[0] for _ in
                             range(1,random.choice(range(self.lower_array_size, 1+self.upper_array_size))+1)])
                for s in range(size)]
      else:
        return [Value(value_type=self.space_type,
                      value=[self.element_value_space.sample()[0] for _ in range(self.exact_array_size)])
                for s in range(size)]
    else:
      if all: return self.set
      else:   return [random.choice(self.set) for _ in range(size)]

class DatasetValueSpace():

  dataset_dir = "/Users/ece/0xdata/h2o-3/smalldata/featureData"
  dataset_counter = 1

  def __init__(self, space_type="dataset", col_value_spaces=None, rows_set=None, rows_lower=None, rows_upper=None,
               cols_set=None, cols_lower=None, cols_upper=None, mixed=False, na=False, colnames=False):
    """
    This class is used to represent an entire domain, or set, of dataset Values. All Values in the domain must be of
    the "dataset" `space_type`.

    :param space_type: "dataset".
    :param col_value_spaces: list of allowable ScalerValueSpaces for the columns. (list of ScalerValueSpaces)
    :param rows_set: set of the allowable number of rows. if this is specified, then `rows_lower` and `rows_upper`
                     should be None. (list of positive integers)
    :param rows_lower: lower bound on the number of allowable rows in the dataset. (integer)
    :param rows_upper: upper bound on the number of allowable rows in the dataset. (integer)
    :param cols_set: set of the allowable number of cols. if this is specified, then `cols_lower` and `cols_upper`
                     should be None. (list of positive integers)
    :param cols_lower: lower bound on the number of allowable cols in the dataset. (integer)
    :param cols_upper: upper bound on the number of allowable cols in the dataset. (integer)
    :param mixed: whether or not columns in the data set can have different Value.value_types. (logical)
    :param na: whether or not the data can have NA values. (logical)
    :param colnames: whether or not the dataset should be given default column names "C1", ... "Cn". (logical)
    """

    self.space_type = space_type
    self.col_value_spaces = col_value_spaces
    self.rows_set = rows_set
    self.rows_lower = rows_lower
    self.rows_upper = rows_upper
    self.cols_set = cols_set
    self.cols_lower = cols_lower
    self.cols_upper = cols_upper
    self.mixed = mixed
    self.na = na
    self.colnames = colnames

  def sample(self):
    """
    !This method persists datasets to disk!

    Pick `size` datasets randomly (with replacement) from the DatasetValueSpace. If `size` is None, then we use the
    following rules to pick datasets.

    Create one dataset Value for each combination of:
    1. ScalerValueSpaces specified in `col_value_spaces` list
    2. each value in num_rows list.
    3. each value in num_cols list.
    4. with and without na values.
    Potentially create one additional dataset whose columns have mixed ScalerValuesSpaces.

    :return: a list of Values from the DatasetValueSpace.
    """

    if (self.rows_set is None) and (self.cols_set is None):
      row_sizes = [int(random.choice(range(self.rows_lower, self.rows_upper + 1)))]
      col_sizes = [int(random.choice(range(self.cols_lower, self.cols_upper + 1)))]
    else:
      row_sizes = self.rows_set
      col_sizes = self.cols_set

    datasets = []
    for cols in col_sizes:
      for rows in row_sizes:
        for val_space in self.col_value_spaces:
          for na in [True, False] if self.na else [False]:
            if na: data_set = [[val_space.sample().value if random.uniform(0, 1) > 0.1 else "NA" for c in range(cols)]
                               for r in range(rows)]
            else:  data_set = [[val_space.sample().value for c in range(cols)] for r in range(rows)]
            if self.colnames: data_set.insert(0,["C{0}".format(i+1) for i in range(cols)])
            with open(os.path.join(DatasetValueSpace.dataset_dir, "{0}.csv".format(DatasetValueSpace.dataset_counter)),
                      "wb") as f:
              csv.writer(f).writerows(data_set)
            print("{0},s3://h2o-public-test-data/smalldata/featureData/{0}.csv".
                  format(DatasetValueSpace.dataset_counter))
            datasets.append(Value(value_type="dataset", value=DatasetValueSpace.dataset_counter))
            DatasetValueSpace.dataset_counter += 1

    # one, mixed data set (uses max of col_sizes, row_sizes)
    if self.mixed and self.col_value_spaces > 1 and max(col_sizes) > 1:
      cols = max(col_sizes)
      rows = max(row_sizes)
      data_set = [[] for r in range(rows)]
      for c in range(cols):
        val_space = self.col_value_spaces[c % len(self.col_value_spaces)]
        column = [[val_space.sample().value] for r in range(rows)]
        data_set = [d+c for d,c in zip(data_set, column)]
      with open(os.path.join(DatasetValueSpace.dataset_dir, "{0}.csv".format(DatasetValueSpace.dataset_counter)),
                "wb") as f:
        csv.writer(f).writerows(data_set)
      print("{0},s3://h2o-public-test-data/smalldata/featureData/{0}.csv".format(DatasetValueSpace.dataset_counter))
      datasets.append(Value(value_type="dataset", value=DatasetValueSpace.dataset_counter))
      DatasetValueSpace.dataset_counter += 1

    return datasets