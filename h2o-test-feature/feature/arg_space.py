from value_space import *

class ArgSpace():
  def __init__(self, name, value_spaces):
    """
    An argument of a feature consists of a set of ValueSpaces. This class is used to represent an argument's domain.

    :param name: the name of the argument's space. (string)
    :param value_spaces: list of ValueSpaces that the argument can have. (list of ValueSpaces)
    """

    self.name = name
    self.value_spaces = value_spaces

  def sample(self):
    """
    Select "points" from the ArgSpace. A "point" is represented by a single-element dictionary, where the key is the
    name of the ArgSpace and the value is a Value in that space. Points are selected as follows:

    For each ValueSpace in `value_spaces`:
    1. If `set` option is specified, then each create a "point" for each element in `set`.
    2. If the `set` option is None, then:
      2a. If the ValueSpace is a ScalerValueSpace or an ArrayValueSpace then pick 3 points at random from the
          ValueSpace.
      2b. If the ValueSpace is a DatasetValueSpace or NullValueSpace, just use the points returned by
          ValueSpace.sample()

    :return: a list of dictionaries [{"ArgSpace.name":Value1}, {"ArgSpace.name":Value2}, ...],
             where each dictionary key is the name of the ArgSpace and the value is the Value that was
             selected.
    """
    points = []
    for val_space in self.value_spaces:
      if isinstance(val_space, (DatasetValueSpace, NullValueSpace)) or not (val_space.set is None):
        for val in val_space.sample(all=True): points.append({self.name: val})
      else:
        if isinstance(val_space, (ScalerValueSpace, ArrayValueSpace)):
          for val in val_space.sample(size=3): points.append({self.name: val})

    return points

"""
########################################################################################################################
                                          Some specific ArgSpaces
########################################################################################################################
"""

class DigitsParameterArgSpace(ArgSpace):
  def __init__(self,
               name="digits"):
    value_spaces = [ScalerValueSpace(space_type="integer",
                                     set=[Value(value_type="integer", value=0),
                                          Value(value_type="integer", value=1),
                                          Value(value_type="integer", value=2),
                                          Value(value_type="integer", value=3),
                                          Value(value_type="integer", value=4),
                                          Value(value_type="integer", value=5),
                                          Value(value_type="integer", value=6)])]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class CenterScaleParameterArgSpace(ArgSpace):
  def __init__(self,
               name="center"):
    value_spaces = [ScalerValueSpace(space_type="logical",
                                     set=[Value(value_type="logical", value=True),
                                          Value(value_type="logical", value=False)]),
                    ArrayValueSpace(space_type="real[]",
                                    exact_array_size=10,
                                    element_value_space=ScalerValueSpace(space_type="real",
                                                                         lower=-10000,
                                                                         upper=10000))]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class LogicalParameterArgSpace(ArgSpace):
  def __init__(self,
               name):
    value_spaces = [ScalerValueSpace(space_type="logical",
                                     set=[Value(value_type="logical", value=True),
                                          Value(value_type="logical", value=False)])]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class IntegerParameterArgSpace(ArgSpace):
  def __init__(self,
               name):
    value_spaces = [ScalerValueSpace(space_type="integer",
                                     lower=-10000,
                                     upper=10000)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class StringParameterArgSpace(ArgSpace):
  def __init__(self,
               name):
    value_spaces=[ScalerValueSpace(space_type="string",
                                   lower=1,
                                   upper=10)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class VarUseParameterArgSpace(ArgSpace):
  def __init__(self,
               name="use",
               na=True):
    if na:
      value_spaces = [ScalerValueSpace(space_type="string",
                                       set=[Value(value_type="string", value="everything"),
                                            Value(value_type="string", value="complete.obs")])]
    else:
      value_spaces = [ScalerValueSpace(space_type="string",
                                       set=[Value(value_type="string", value="all.obs")])]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class ProbsParameterArgSpace(ArgSpace):
  def __init__(self,
               name="probs"):
    value_spaces=[ArrayValueSpace(space_type="real[]",
                                  exact_array_size=10,
                                  element_value_space=ScalerValueSpace(space_type="real",
                                                                       lower=0,
                                                                       upper=1))]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class BreaksParameterArgSpace(ArgSpace):
  def __init__(self,
               name="breaks"):
    value_spaces=[ArrayValueSpace(space_type="real[]",
                                  exact_array_size=3,
                                  element_value_space=ScalerValueSpace(space_type="real",
                                                                       lower=0,
                                                                       upper=1))]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class HistBreaksUseParameterArgSpace(ArgSpace):
  def __init__(self,
               name="breaks"):
    value_spaces = [ScalerValueSpace(space_type="string",
                                     set=[Value(value_type="string", value="Sturges"),
                                          Value(value_type="string", value="Rice"),
                                          Value(value_type="string", value="sqrt"),
                                          Value(value_type="string", value="Doane"),
                                          Value(value_type="string", value="FD"),
                                          Value(value_type="string", value="Scott")])]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class LabelsParameterArgSpace(ArgSpace):
  def __init__(self,
               name="labels"):
    value_spaces=[ArrayValueSpace(space_type="string[]",
                                  exact_array_size=4,
                                  element_value_space=ScalerValueSpace(space_type="string",
                                                                       lower=1,
                                                                       upper=3)),
                  NullValueSpace()]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class NullParameterArgSpace(ArgSpace):
  def __init__(self,
               name):
    value_spaces=[NullValueSpace()]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)


class DigLabParameterArgSpace(ArgSpace):
  def __init__(self,
               name="dig.lab"):
    value_spaces=[ScalerValueSpace(space_type="integer",
                                   lower=0,
                                   upper=12)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class MatchTableParameterArgSpace(ArgSpace):
  def __init__(self,
               name="table"):
    value_spaces=[ArrayValueSpace(space_type="enum[]",
                                  exact_array_size=1,
                                  element_value_space=ScalerValueSpace(space_type="enum",
                                                                       set=[Value(value_type="enum",
                                                                                  value="c")]))]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class MatchIncomparablesParameterArgSpace(ArgSpace):
  def __init__(self,
               name="incomparables"):
    value_spaces=[ArrayValueSpace(space_type="enum[]",
                                  exact_array_size=1,
                                  element_value_space=ScalerValueSpace(space_type="enum",
                                                                       set=[Value(value_type="enum",
                                                                                  value="b")]))]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

"""
# Dataset arg spaces
"""
class RealDataArgSpace(ArgSpace):
  def __init__(self,
               name="x",
               na=True,
               colnames=False,
               null=False):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                         lower=-10000,
                                                                         upper=10000)],
                                      rows_set = [10],
                                      cols_set = [10],
                                      na=na,
                                      colnames=colnames)]
    if null: value_spaces.append(NullValueSpace())
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class TableDataArgSpace(ArgSpace):
  def __init__(self,
               name="x",
               two_col = False):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="integer",
                                                                         lower=-10000,
                                                                         upper=10000)],
                                      rows_set = [100],
                                      cols_set = [2] if two_col else [1],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class MinusOneToOneDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):

    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                         lower=-1,
                                                                         upper=1)],
                                      rows_set = [10],
                                      cols_set = [10],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class MinusTenToTenDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                         lower=-10,
                                                                         upper=10)],
                                      rows_set = [10],
                                      cols_set = [10],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class OneToInfDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                         lower=1,
                                                                         upper=10000)],
                                      rows_set = [10],
                                      cols_set = [10],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class ZeroToInfDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                                    lower=0,
                                                                                    upper=10000)],
                                      rows_set = [10],
                                      cols_set = [10],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class ZeroToTenDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                                    lower=0,
                                                                                    upper=10)],
                                      rows_set = [10],
                                      cols_set = [10],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class ZeroToOneDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                                    lower=0,
                                                                                    upper=1)],
                                      rows_set = [10],
                                      cols_set = [10],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class ZeroOneDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="integer",
                                                                         set=[Value(value_type="integer",
                                                                                    value=0),
                                                                              Value(value_type="integer",
                                                                                    value=1)])],
                                      rows_set = [10],
                                      cols_set = [10],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class IsCharDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                         lower=-10000,
                                                                         upper=10000),
                                                        ScalerValueSpace(space_type="string",
                                                                         lower=1,
                                                                         upper=10)],
                                     rows_set = [100],
                                     cols_set = [1],
                                     na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class IsNaDataArgSpace(ArgSpace):
  def __init__(self, 
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                         lower=-10000,
                                                                         upper=10000),
                                                        ScalerValueSpace(space_type="string",
                                                                         lower=1,
                                                                         upper=10)],
                                     rows_set = [10],
                                     cols_set = [10],
                                     na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

# TODO: how does h2o determine whether or not a column is a string or factor?
class LevelsDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="string",
                                                                         set=[Value(value_type="string",
                                                                                    value="a"),
                                                                              Value(value_type="string",
                                                                                    value="b"),
                                                                              Value(value_type="string",
                                                                                    value="c"),
                                                                              Value(value_type="string",
                                                                                    value="d")])],
                                      rows_set = [100],
                                      cols_set = [1],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class MinusOneToInfDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                         lower=-1,
                                                                         upper=10000)],
                                      rows_set = [10],
                                      cols_set = [10],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class NcolDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                         lower=-10000,
                                                                         upper=10000)],
                                      rows_set = [10],
                                      cols_set = [1, 10, 33],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class NrowDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="real",
                                                                         lower=-10000,
                                                                         upper=10000)],
                                      rows_set = [1, 10, 33],
                                      cols_set = [10],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class NotDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="integer",
                                                                         set=[Value(value_type="integer",
                                                                                    value=0),
                                                                              Value(value_type="integer",
                                                                                    value=1)])],
                                      rows_set = [10],
                                      cols_set = [10],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class AllDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="integer",
                                                                         set=[Value(value_type="integer",
                                                                                    value=0)]),
                                                        ScalerValueSpace(space_type="integer",
                                                                         set=[Value(value_type="integer",
                                                                                    value=1)]),
                                                        ScalerValueSpace(space_type="integer",
                                                                         set=[Value(value_type="integer",
                                                                                    value=0),
                                                                              Value(value_type="integer",
                                                                                    value=1)])],
                                      rows_set = [10],
                                      cols_set = [10],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class MatchDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="enum",
                                                                         set=[Value(value_type="enum",
                                                                                    value="a"),
                                                                              Value(value_type="enum",
                                                                                    value="b"),
                                                                              Value(value_type="enum",
                                                                                    value="c")])],
                                     rows_set = [100],
                                     cols_set = [1],
                                     na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)

class StringDataArgSpace(ArgSpace):
  def __init__(self,
               name="x"):
    value_spaces = [DatasetValueSpace(col_value_spaces=[ScalerValueSpace(space_type="string",
                                                                         lower=1,
                                                                         upper=10)],
                                      rows_set = [100],
                                      cols_set = [1],
                                      na=True)]
    ArgSpace.__init__(self, name=name, value_spaces=value_spaces)