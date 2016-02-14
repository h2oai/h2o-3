import random
import string

class Value:
  def __init__(self, value_type, value):
    """
    This class is used to represent an element in the domain of some value space. The allowable value spaces are the
    ScalerValueSpace, ArrayValueSpace, and the DatasetValueSpace. See these classes for further details.

    :param value_type: the type of the element. the allowable types are "integer", "real", "string", "enum", "logical",
                       "integer[]", "real[]", "string[]", "enum[]", "logical[]", and "dataset". (string)
    :param value: they python representation of the element.
    """

    self.value_type = value_type
    self.value = value

class ScalerValueSpace():
  def __init__(self, space_type, set=None, lower=None, upper=None):
    """
    This class is used to represent an entire domain, or set, of Values. One restriction of this class is that
    all Values in the domain must be of the same Value.value_type. Allowable Value.value_types for this class are
    "integer", "real", "string", "enum", and "logical". Another restriction of this class is that either set OR (lower
    AND upper) can be specified, but not both.

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

    :param all: return all of the values in `self.set`. ignored if `self.set` is None. (logical)
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