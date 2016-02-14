from value_space import *

class ArgSpace():
  def __init__(self, name, value_spaces, null=False):
    """

    :param name: the name of the space. (string)
    :param value_spaces: list of allowable ValueSpaces. (list)
    """

    self.name = name
    self.value_spaces = value_spaces

  def sample(self):
    """
    Retrieve one or more "points" from the ArgSpace. Here, a point is represented by a single-element
    dictionary, where the key is the name of the ArgSpace and the value is a Value in that space.

    For each ValueSpace in `value_spaces` if `set` option is specified, then each create a "point" for each `set`
    element, otherwise just create one "point" in the range of ValueSpace.

    :param min_v: passed to ValueSpace.sample().
    :param max_v: passed to ValueSpace.sample().
    :param max_array_size: passed to ValueSpace.sample().
    :param exact_array_size: passed to ValueSpace.sample().

    :return: a list of dictionaries [{"ArgSpace.name":Value1}, {"ArgSpace.name":Value2}, ...],
             where each dictionary key is the name of the ArgSpace and the value is the Value that was
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

class DigitsArgSpace(ArgSpace):
  def __init__(self,
               name="digits",
               value_spaces=[ScalerValueSpace(space_type="integer",
                                              set=[0,1,2,3,4,5,6])],
               null=False):
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class CenterScaleArgSpace(ArgSpace):
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
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class LogicalArgSpace(ArgSpace):
  def __init__(self,
               name,
               value_spaces=[ScalerValueSpace(space_type="logical",
                                              set=[True, False])],
               null=False):
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class IntegerArgSpace(ArgSpace):
  def __init__(self,
               name,
               value_spaces=[ScalerValueSpace(space_type="integer",
                                              lower=-10000,
                                              upper=10000)],
               null=False):
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class StringArgSpace(ArgSpace):
  def __init__(self,
               name,
               value_spaces=[ScalerValueSpace(space_type="string",
                                              lower=1,
                                              upper=10)],
               null=False):
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class VarUseArgSpace(ArgSpace):
  def __init__(self,
               name="use",
               null=False,
               na=True):
    value_spaces = [ScalerValueSpace(space_type="string", set=["everything", "complete.obs"])] if na else \
      [ScalerValueSpace(space_type="string", set=["all.obs"])]
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class ProbsArgSpace(ArgSpace):
  def __init__(self,
               name="probs",
               value_spaces=[ArrayValueSpace(space_type="real[]",
                                             exact_array_size=10,
                                             element_value_space=ScalerValueSpace(space_type="real",
                                                                                  lower=0,
                                                                                  upper=1))],
               null=False):
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class BreaksArgSpace(ArgSpace):
  def __init__(self,
               name="breaks",
               value_spaces=[ArrayValueSpace(space_type="real[]",
                                             exact_array_size=3,
                                             element_value_space=ScalerValueSpace(space_type="real",
                                                                                  lower=0,
                                                                                  upper=1))],
               null=False):
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class LabelsArgSpace(ArgSpace):
  def __init__(self,
               name="labels",
               value_spaces=[ArrayValueSpace(space_type="string[]",
                                             exact_array_size=4,
                                             element_value_space=ScalerValueSpace(space_type="string",
                                                                                  lower=1,
                                                                                  upper=3))],
               null=True):
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)


class DigLabArgSpace(ArgSpace):
  def __init__(self,
               name="dig.lab",
               value_spaces=[ScalerValueSpace(space_type="integer",
                                              lower=0,
                                              upper=12)],
               null=False):
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class MatchTableArgSpace(ArgSpace):
  def __init__(self,
               name="table",
               value_spaces=[ArrayValueSpace(space_type="enum[]",
                                             exact_array_size=1,
                                             element_value_space=ScalerValueSpace(space_type="enum",
                                                                                  set=["c"]))],
               null=False):
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)

class MatchIncomparablesArgSpace(ArgSpace):
  def __init__(self,
               name="incomparables",
               value_spaces=[ArrayValueSpace(space_type="enum[]",
                                             exact_array_size=1,
                                             element_value_space=ScalerValueSpace(space_type="enum",
                                                                                  set=["b"]))],
               null=False):
    ArgSpace.__init__(self,
                               name=name,
                               value_spaces=value_spaces,
                               null=null)