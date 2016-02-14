from value_space import *

class DataArgSpace():

  def __init__(self, name, col_value_spaces, rows_set=None, rows_lower=None, rows_upper=None, cols_set=None,
               cols_lower=None, cols_upper=None, mixed=False, null=False, na=False, colnames=False):
    """
    A data subspace in a FeatureSpace.

    :param name: the name of the space. (string)
    :param col_value_spaces: list of allowable ValueSpaces for the columns. (list)
    :param rows_set: set of allowable rows. if this is set, rows_lower and rows_upper should be None. (list)
    :param rows_lower: lower bound on the number of allowable rows in the dataset. (integer)
    :param rows_upper: upper bound on the number of allowable rows in the dataset. (integer)
    :param cols_set: set of allowable cols. if this is set, cols_lower and cols_upper should be None. (list)
    :param cols_lower: lower bound on the number of allowable cols in the dataset. (integer)
    :param cols_upper: upper bound on the number of allowable cols in the dataset. (integer)
    :param mixed: whether or not columns in the data set can have different ValueSpaces. (logical)
    :param null: whether or not the argument can be NULL. (logical)
    :param na: whether or not the data can have NA values. (logical)
    """

    self.name = name
    self.col_value_spaces = col_value_spaces
    self.rows_set = rows_set
    self.rows_lower = rows_lower
    self.rows_upper = rows_upper
    self.cols_set = cols_set
    self.cols_lower = cols_lower
    self.cols_upper = cols_upper
    self.mixed = mixed
    self.null = null
    self.na = na
    self.colnames = colnames

  def sample(self, rows_max=None, rows_min=None, cols_max=None, cols_min=None, min_v=None, max_v=None):
    """
    Retrieve one or more "points" from the DataArgSpace. Here, a point is represented by a single-element dictionary,
    where the key is the name of the DataArgSpace and the value is a Dataset in that space.

    This method persists data sets to disk!
    This method uses global state variable to keep track of data sets persisted to disk!

    Create one "point" for each combination of:
    1. ValueSpaces specified in col_value_spaces list
    2. each value in num_rows list.
    3. each value in num_cols list.
    4. na.

    Potentially create one additional "point" with mixed ValuesSpaces.

    :param rows_max: the maximum number of rows that the data set can have. (integer)
    :param rows_min: the minimum number of rows that the data set can have. (integer)
    :param cols_max: the maximum number of cols that the data set can have. (integer)
    :param cols_min: the minimum number of cols that the data set can have. (integer)
    :param min_v: passed to ScalerValueSpace.sample().
    :param max_v: passed to ScalerValueSpace.sample().

    :return: a list of dictionaries [{"DataArgSpace.name":Dataset1}, {"DataArgSpace.name":Dataset2}, ...], where each
             dictionary key is the name of the DataArgSpace and the value is the Dataset that was generated.
    """

    if self.col_value_spaces == []: return [{self.name: None}] # empty Dataset

    if (self.rows_set is None) and (self.cols_set is None):
      rows_max = self.rows_upper if rows_max is None else rows_max
      rows_min = self.rows_lower if rows_min is None else rows_min
      cols_max = self.cols_upper if cols_max is None else cols_max
      cols_min = self.cols_lower if cols_min is None else cols_min
      row_sizes = [int(random.choice(range(rows_min, rows_max + 1)))]
      col_sizes = [int(random.choice(range(cols_min, cols_max + 1)))]
    else:
      row_sizes = self.rows_set
      col_sizes = self.cols_set

    data_sets = []
    for cols in col_sizes:
      for rows in row_sizes:
        for val_space in self.col_value_spaces:
          for na in [True, False] if self.na else [False]:
            if na: data_set = [[val_space.sample(min_v=min_v, max_v=max_v).value if random.uniform(0, 1) > 0.1
                                else "NA" for c in range(cols)] for r in range(rows)]
            else:  data_set = [[val_space.sample(min_v=min_v, max_v=max_v).value for c in range(cols)]
                               for r in range(rows)]
            if self.colnames: data_set.insert(0,["C{0}".format(i+1) for i in range(cols)])
            with open(os.path.join(DataArgSpace.data_set_dir, "{0}.csv".
                    format(DataArgSpace.data_set_counter)), "wb") as f:
              csv.writer(f).writerows(data_set)
            print("{0},s3://h2o-public-test-data/smalldata/featureData/{0}.csv".format(DataArgSpace.data_set_counter))
            data_sets.append({self.name: Dataset(id=DataArgSpace.data_set_counter, rows=rows, cols=cols)})
            DataArgSpace.data_set_counter += 1

    # one, mixed data set (uses max of col_sizes, row_sizes)
    if self.mixed and self.col_value_spaces > 1 and max(col_sizes) > 1:
      cols = max(col_sizes)
      rows = max(row_sizes)
      data_set = [[] for r in range(rows)]
      for c in range(cols):
        val_space = self.col_value_spaces[c % len(self.col_value_spaces)]
        column = [[val_space.sample(min_v=min_v, max_v=max_v).value] for r in range(rows)]
        data_set = [d+c for d,c in zip(data_set, column)]
      with open(os.path.join(DataArgSpace.data_set_dir, "{0}.csv".format(DataArgSpace.data_set_counter)), "wb") as f:
        csv.writer(f).writerows(data_set)
      print("{0},s3://h2o-public-test-data/smalldata/featureData/{0}.csv".format(DataArgSpace.data_set_counter))
      data_sets.append({self.name: Dataset(id=DataArgSpace.data_set_counter, rows=rows, cols=cols)})
      DataArgSpace.data_set_counter += 1

    # data argument can be NULL
    if self.null: data_sets.append({self.name: None})

    return data_sets

class RealDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=-10000,
                                                  upper=10000)],
               rows_set = [10],
               cols_set = [10],
               na=True,
               colnames=False):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na,
                          colnames=colnames)

class TableDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="integer",
                                                  lower=-10000,
                                                  upper=10000)],
               rows_set = [100],
               cols_set = None,
               na=True,
               two_col=False,
               null=False):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=[2] if two_col else [1],
                          na=na,
                          null=null)

class MinusOneToOneDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=-1,
                                                  upper=1)],
               rows_set = [10],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class MinusTenToTenDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=-10,
                                                  upper=10)],
               rows_set = [10],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class OneToInfDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=1,
                                                  upper=10000)],
               rows_set = [10],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class ZeroToInfDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=0,
                                                  upper=10000)],
               rows_set = [10],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class ZeroToTenDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=0,
                                                  upper=10)],
               rows_set = [10],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class ZeroToOneDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=0,
                                                  upper=1)],
               rows_set = [10],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class ZeroOneDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="integer",
                                                  set=[0,1])],
               rows_set = [10],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class IsCharDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=-10000,
                                                  upper=10000),
                                 ScalerValueSpace(space_type="string",
                                                  lower=1,
                                                  upper=10)],
               rows_set = [100],
               cols_set = [1],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class IsNaDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=-10000,
                                                  upper=10000),
                                 ScalerValueSpace(space_type="string",
                                                  lower=1,
                                                  upper=10)],
               rows_set = [10],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

# TODO: how does h2o determine whether or not a column is a string or factor?
class LevelsDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="string",
                                                  set=["a", "b", "c", "d"])],
               rows_set = [100],
               cols_set = [1],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class MinusOneToInfDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=-1,
                                                  upper=10000)],
               rows_set = [10],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class NcolDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=-10000,
                                                  upper=10000)],
               rows_set = [10],
               cols_set = [1, 10, 33],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class NrowDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="real",
                                                  lower=-10000,
                                                  upper=10000)],
               rows_set = [1, 10, 33],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class NotDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="integer",
                                                  set=[0,1])],
               rows_set = [10],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class AllDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="integer", set=[0]),
                                 ScalerValueSpace(space_type="integer", set=[1]),
                                 ScalerValueSpace(space_type="integer", set=[0,1])],
               rows_set = [10],
               cols_set = [10],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class MatchDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="enum", set=["a", "b", "c"])],
               rows_set = [100],
               cols_set = [1],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)

class StringDataArgSpace(DataArgSpace):
  def __init__(self, name="x",
               col_value_spaces=[ScalerValueSpace(space_type="string",
                                                  lower=1,
                                                  upper=10)],
               rows_set = [100],
               cols_set = [1],
               na=True):
    DataArgSpace.__init__(self,
                          name=name,
                          col_value_spaces=col_value_spaces,
                          rows_set=rows_set,
                          cols_set=cols_set,
                          na=na)