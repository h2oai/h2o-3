import random
import string

class Value:
  def __init__(self, value_type, value):
    self.value_type = value_type
    self.value = value

class ScalerValueSpace():
  def __init__(self, space_type, set=None, lower=None, upper=None):
    """
    The allowable scaler values for a ParameterArgSpace or an element in a data set.

    :param space_type: Allowable types are "integer", "real", "string", "enum", and "logical." (string)
    :param set: set of allowable values. (list)
    """
    self.space_type = space_type
    self.set = set
    self.lower = lower
    self.upper = upper

  def sample(self, min_v=None, max_v=None, all=False):
    """
    Pick a Value from the set of values provided in `self.set` or in the range of allowable values specified by
    `min_v` and `max_v`. `min_v` and `max_v` are ignored if `self.set` is specified.
    :param min_v: the minimum value (in the range `self.lower` to `self.upper`) that can be selected.
    :param max_v: the maximum value (in the range `self.lower` to `self.upper`) that can be selected.
    :param all: return all of the values in `self.set`. ignored if `self.set` is None. (logical)
    :return: a single Value from the ScalerValueSpace, or all Values from `self.set` if `all` is true
    """

    if self.set is None:
      min_v, max_v  = (self.lower if min_v is None else min_v, self.upper if max_v is None else max_v)
      if self.space_type == "integer" or self.space_type == "real":
        return Value(value_type=self.space_type,
                     value=random.uniform(min_v, max_v) if self.space_type == "real" else int(random.uniform(min_v, max_v)))
      elif self.space_type == "string" or self.space_type == "enum":
        return Value(value_type=self.space_type,
                     value=''.join(random.choice(string.ascii_uppercase + string.digits + string.ascii_lowercase)
                                   for _ in range(random.choice(range(min_v, max_v+1)))))
    else:
      if not all: return Value(value_type=self.space_type, value=random.choice(self.set))
      else: return [Value(value_type=self.space_type, value=v) for v in self.set]

class ArrayValueSpace:
  def __init__(self, space_type, element_value_space, max_array_size=None, exact_array_size=None, sort=False):
    """
    The allowable array values for a ParameterArgSpace.

    :param space_type: "integer[]", "real[]", "string[]", "enum[]"
    :param max_array_size: max allowable size of an array
    :param exact_array_size: exact size of an array
    :param element_value_space: the value space for an array's individual elements
    :param sort: sort the array upon sampling.
    :return:
    """
    self.space_type = space_type
    self.max_array_size = max_array_size
    self.exact_array_size = exact_array_size
    self.element_value_space = element_value_space
    self.sort = sort

  def sample(self, max_array_size=None, exact_array_size=None, min_v=None, max_v=None):
    """
    Pick a Value from the allowable ArrayValueSpace, subject to the constraints `max_array_size`, `exact_array_size`,
    `min_v` and `max_v`. Only one of `exact_array_size` and `max_array_size` can be specified.

    :param max_array_size: the maximum size of the array to be selected.
    :param exact_array_size: the exact size of the array to be selected.
    :param min_v: the minimum value of an array element to be selected.
    :param max_v: the maximum value of an array element to be selected.
    :return: a single point from the ArrayValueSpace
    """
    if max_array_size is None: max_array_size = self.max_array_size
    if exact_array_size is None: exact_array_size = self.exact_array_size
    if not (max_array_size is None) and not (exact_array_size is None): raise(ValueError, "both max and exact!")

    if max_array_size is None:
      value = [self.element_value_space.sample(min_v=min_v, max_v=max_v).value for _ in range(exact_array_size)]
    else:
      value = [self.element_value_space.sample(min_v=min_v, max_v=max_v).value for _ in
               range(1,random.choice(range(1, 1+max_array_size))+1)]

    if self.sort: value = sorted(value)
    return Value(value_type=self.space_type, value=value)