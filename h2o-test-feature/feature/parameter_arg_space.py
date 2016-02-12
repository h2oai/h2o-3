from value_space import *

class ParameterArgSpace():
  def __init__(self, name, value_spaces, null=False):
    """
    A parameter subspace in a FeatureSpace.

    :param name: the name of the space. (string)
    :param value_spaces: list of allowable ValueSpaces for the columns. (list)
    :param null: whether or not the argument can be NULL. (logical)
    """

    self.name = name
    self.value_spaces = value_spaces
    self.null = null

  def sample(self, min_v=None, max_v=None, max_array_size=None, exact_array_size=None):
    """
    Retrieve one or more "points" from the ParameterArgSpace. Here, a point is represented by a single-element
    dictionary, where the key is the name of the ParameterArgSpace and the value is a Value in that space.

    For each ValueSpace in `value_spaces` if `set` option is specified, then each create a "point" for each `set`
    element, otherwise just create one "point" in the range of ValueSpace.

    :param min_v: passed to ValueSpace.sample().
    :param max_v: passed to ValueSpace.sample().
    :param max_array_size: passed to ValueSpace.sample().
    :param exact_array_size: passed to ValueSpace.sample().

    :return: a list of dictionaries [{"ParameterArgSpace.name":Value1}, {"ParameterArgSpace.name":Value2}, ...],
             where each dictionary key is the name of the ParameterArgSpace and the value is the Value that was
             generated.
    """
    points = []
    for val_space in self.value_spaces:
      if isinstance(val_space, ScalerValueSpace):
        if not (val_space.set is None):
          for value in val_space.sample(all=True): points.append({self.name: value})
        else:
          points.append({self.name: val_space.sample(min_v=min_v, max_v=max_v)})
      else:
        points.append({self.name: val_space.sample(min_v=min_v, max_v=max_v, max_array_size=max_array_size,
                                                   exact_array_size=exact_array_size)})

    if self.null: points.append({self.name: None})

    return points

class DigitsParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name="digits",
               value_spaces=[ScalerValueSpace(space_type="integer",
                                              set=[0,1,2,3,4,5,6])],
               null=False):
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class CenterScaleParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name="center",
               value_spaces=[ScalerValueSpace(space_type="logical",
                                              set=[True, False]),
                             ArrayValueSpace(space_type="real[]",
                                             exact_array_size=10,
                                             element_value_space=ScalerValueSpace(space_type="real",
                                                                                  lower=-10000,
                                                                                  upper=10000))],
               null=False):
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class LogicalParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name,
               value_spaces=[ScalerValueSpace(space_type="logical",
                                              set=[True, False])],
               null=False):
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class IntegerParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name,
               value_spaces=[ScalerValueSpace(space_type="integer",
                                              lower=-10000,
                                              upper=10000)],
               null=False):
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class StringParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name,
               value_spaces=[ScalerValueSpace(space_type="string",
                                              lower=1,
                                              upper=10)],
               null=False):
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class VarUseParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name="use",
               null=False,
               na=True):
    value_spaces = [ScalerValueSpace(space_type="string", set=["everything", "complete.obs"])] if na else \
      [ScalerValueSpace(space_type="string", set=["all.obs"])]
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class ProbsParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name="probs",
               value_spaces=[ArrayValueSpace(space_type="real[]",
                                             exact_array_size=10,
                                             element_value_space=ScalerValueSpace(space_type="real",
                                                                                  lower=0,
                                                                                  upper=1))],
               null=False):
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class BreaksParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name="breaks",
               value_spaces=[ArrayValueSpace(space_type="real[]",
                                             exact_array_size=3,
                                             element_value_space=ScalerValueSpace(space_type="real",
                                                                                  lower=0,
                                                                                  upper=1))],
               null=False):
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class LabelsParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name="labels",
               value_spaces=[ArrayValueSpace(space_type="string[]",
                                             exact_array_size=4,
                                             element_value_space=ScalerValueSpace(space_type="string",
                                                                                  lower=1,
                                                                                  upper=3))],
               null=True):
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)


class DigLabParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name="dig.lab",
               value_spaces=[ScalerValueSpace(space_type="integer",
                                              lower=0,
                                              upper=12)],
               null=False):
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class MatchTableParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name="table",
               value_spaces=[ArrayValueSpace(space_type="enum[]",
                                             exact_array_size=1,
                                             element_value_space=ScalerValueSpace(space_type="enum",
                                                                                  set=["c"]))],
               null=False):
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class MatchIncomparablesParameterArgSpace(ParameterArgSpace):
  def __init__(self,
               name="incomparables",
               value_spaces=[ArrayValueSpace(space_type="enum[]",
                                             exact_array_size=1,
                                             element_value_space=ScalerValueSpace(space_type="enum",
                                                                                  set=["b"]))],
               null=False):
    ParameterArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)