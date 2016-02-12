import itertools
from data_arg_space import *
from parameter_arg_space import *
from feature_space_sample import FeatureSpaceSample

class FeatureSpace():
  def __init__(self, name):
    self.name = name

  def combine_subspace_samplings(self, subspace_samplings, method="cartesian"):
    """
    Combine subspace samplings. For example, given a DataArgSpace and a FeatureArgSpace, samplings of each subspace
    can be computed with DataArgSpace.sample() and FeatureArgSpace.sample(), giving
    [{"DataArgSpace.name":Dataset1}, {"DataArgSpace.name":Dataset2}, ...] and
    [{"ParameterArgSpace.name":Value1}, {"ParameterArgSpace.name":Value2}, ...], respectively. This routine combines
    these samplings according to the `method` option. The result is a list of "points" in the FeatureSpace, which
    may look something like
    [{"DataArgSpace.name":Dataset1, "ParameterArgSpace.name":Value1},
     {"DataArgSpace.name":Dataset2, "ParameterArgSpace.name":Value1},
     {"DataArgSpace.name":Dataset1, "ParameterArgSpace.name":Value2},
     {"DataArgSpace.name":Dataset2, "ParameterArgSpace.name":Value2}]
    :param subspace_samplings: a list of samplings from various subspaces. (list)
    :param method: the method of combination
    :return:
    """

    points = []
    if method == "cartesian":
      for e in itertools.product(*subspace_samplings):
        z = {}
        for d in e: z.update(d)
        points.append(z)
    else:
      raise(ValueError, "Only cartesian method is implemented!")

    return points

class CosFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "cos")
    self.arg_subspaces = (RealDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class ACosFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "acos")
    self.arg_subspaces = (MinusOneToOneDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class CoshFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "cosh")
    self.arg_subspaces = (MinusTenToTenDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class ACoshFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "acosh")
    self.arg_subspaces = (MinusOneToInfDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class SinFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "sin")
    self.arg_subspaces = (RealDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class ASinFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "asin")
    self.arg_subspaces = (MinusOneToOneDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class SinhFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "sinh")
    self.arg_subspaces = (MinusTenToTenDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class ASinhFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "asinh")
    self.arg_subspaces = (RealDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class TanFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "tan")
    self.arg_subspaces = (RealDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class ATanFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "atan")
    self.arg_subspaces = (RealDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class TanhFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "tanh")
    self.arg_subspaces = (MinusTenToTenDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class ATanhFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "atanh")
    self.arg_subspaces = (MinusOneToOneDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class AbsFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "abs")
    self.arg_subspaces = (RealDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class CeilingFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "ceiling")
    self.arg_subspaces = (RealDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class DigammaFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "digamma")
    self.arg_subspaces = (ZeroToInfDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class TrigammaFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "trigamma")
    self.arg_subspaces = (ZeroToInfDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class ExpFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "exp")
    self.arg_subspaces = (MinusTenToTenDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class Expm1FeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "expm1")
    self.arg_subspaces = (MinusTenToTenDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class FloorFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "floor")
    self.arg_subspaces = (RealDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class TruncFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "trunc")
    self.arg_subspaces = (RealDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class GammaFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "gamma")
    self.arg_subspaces = (ZeroToTenDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class IsCharFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "is.character")
    self.arg_subspaces = (IsCharDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class IsNaFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "is.na")
    self.arg_subspaces = (IsNaDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class IsNumericFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "is.numeric")
    self.arg_subspaces = (IsCharDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class LGammaFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "lgamma")
    self.arg_subspaces = (ZeroToInfDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class LevelsFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "h2o.levels")
    self.arg_subspaces = (LevelsDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class LogFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "log")
    self.arg_subspaces = (ZeroToInfDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class Log10FeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "log10")
    self.arg_subspaces = (ZeroToInfDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class Log1pFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "log1p")
    self.arg_subspaces = (MinusOneToInfDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class Log2FeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "log2")
    self.arg_subspaces = (ZeroToInfDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class NLevelsFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "h2o.nlevels")
    self.arg_subspaces = (LevelsDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class NcolFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "ncol")
    self.arg_subspaces = (NcolDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class NrowFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "nrow")
    self.arg_subspaces = (NrowDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class NotFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "!")
    self.arg_subspaces = (NotDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class SignFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "sign")
    self.arg_subspaces = (RealDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class SqrtFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "sqrt")
    self.arg_subspaces = (ZeroToInfDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self): return FeatureSpaceSample(self.name, self.arg_subspaces[0].sample())

class RoundFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "round")
    self.arg_subspaces = (RealDataArgSpace(), DigitsParameterArgSpace()) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class SignifFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "signif")
    self.arg_subspaces = (RealDataArgSpace(), DigitsParameterArgSpace()) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class AndFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "&")
    self.arg_subspaces = (ZeroOneDataArgSpace(), ZeroOneDataArgSpace(name="y")) if arg_subspaces is None \
      else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class OrFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "|")
    self.arg_subspaces = (ZeroOneDataArgSpace(), ZeroOneDataArgSpace(name="y")) if arg_subspaces is None \
      else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class DivFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "/")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class ModFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "%%")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class IntDivFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "%/%")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class MultFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "*")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class PlusFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "+")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class PowFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "^")
    self.arg_subspaces = (MinusTenToTenDataArgSpace(), MinusTenToTenDataArgSpace(name="y")) if arg_subspaces is None \
      else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class SubtFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "-")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class GEFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, ">=")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class GTFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, ">")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))
class LEFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "<=")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class LTFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "<")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class EQFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "==")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class NEFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "!=")
    self.arg_subspaces = (RealDataArgSpace(), RealDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class ScaleFeatureSpace(FeatureSpace): # scale(x, center = TRUE, scale = TRUE)
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "scale")
    self.arg_subspaces = (RealDataArgSpace(), CenterScaleParameterArgSpace(),
                          CenterScaleParameterArgSpace(name="scale")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample(),
                                                                               self.arg_subspaces[2].sample()]))

class AllFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "all")
    self.arg_subspaces = (AllDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample()]))

class CbindFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "h2o.cbind")
    self.arg_subspaces = (RealDataArgSpace(name="x"), RealDataArgSpace(name="y"), RealDataArgSpace(name="z")) \
      if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample(),
                                                                               self.arg_subspaces[2].sample()]))

class ColnamesFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "colnames")
    self.arg_subspaces = (RealDataArgSpace(name="x", colnames=True), LogicalParameterArgSpace(name="do.NULL"),
                          StringParameterArgSpace(name="prefix")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample(),
                                                                               self.arg_subspaces[2].sample()]))

class SliceFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "[")
    i_j_value_spaces = [ScalerValueSpace(space_type="integer",
                                         lower=1,
                                         upper=10),
                        ArrayValueSpace(space_type="integer[]",
                                        element_value_space=ScalerValueSpace(space_type="integer",
                                                                             lower=1,
                                                                             upper=10),
                                        max_array_size=10,
                                        sort=True)]
    self.arg_subspaces = (RealDataArgSpace(name="data"), ParameterArgSpace(name="row", value_spaces=i_j_value_spaces),
                          ParameterArgSpace(name="col", value_spaces=i_j_value_spaces)) if arg_subspaces is None \
      else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample(),
                                                                               self.arg_subspaces[2].sample()]))

class TableFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None, two_col=False):
    FeatureSpace.__init__(self, "h2o.table")
    if two_col:
      self.arg_subspaces = (TableDataArgSpace(two_col=two_col),) if arg_subspaces is None else arg_subspaces
    else:
      self.arg_subspaces = (TableDataArgSpace(),
                            TableDataArgSpace(name="y")) if arg_subspaces is None else arg_subspaces

  def sample(self):
    if len(self.arg_subspaces) == 2:
      return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))
    else:
      return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample()]))

class QuantileFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "h2o.quantile")
    data = RealDataArgSpace(na=False)
    data.cols_set = [1]
    data.rows_set = [100]
    self.arg_subspaces = (data, ProbsParameterArgSpace()) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class CutFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "cut")
    data = ZeroToOneDataArgSpace()
    data.cols_set = [1]
    data.rows_set = [100]
    self.arg_subspaces = (data,
                          BreaksParameterArgSpace(),
                          LogicalParameterArgSpace(name="include.lowest"),
                          LogicalParameterArgSpace(name="right"),
                          DigLabParameterArgSpace()) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample(),
                                                                               self.arg_subspaces[2].sample(),
                                                                               self.arg_subspaces[3].sample(),
                                                                               self.arg_subspaces[4].sample()]))

class MatchFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "h2o.match")
    self.arg_subspaces = (MatchDataArgSpace(),
                          MatchTableParameterArgSpace(),
                          IntegerParameterArgSpace(name="nomatch"),
                          MatchIncomparablesParameterArgSpace()) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample(),
                                                                               self.arg_subspaces[2].sample(),
                                                                               self.arg_subspaces[3].sample()]))

class WhichFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "h2o.which")
    data = ZeroOneDataArgSpace()
    data.cols_set = [1]
    data.rows_set = [100]
    self.arg_subspaces = (data,) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample()]))

class RepLenFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "h2o.rep_len")
    param = IntegerParameterArgSpace(name="length.out")
    param.value_spaces[0].lower = None
    param.value_spaces[0].upper = None
    param.value_spaces[0].set = [1, 10, 15, 42]
    self.arg_subspaces = (RealDataArgSpace(), param) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class StrSplitFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "h2o.strsplit")
    param =  StringParameterArgSpace(name="split")
    param.value_spaces[0].upper = 1
    self.arg_subspaces = (StringDataArgSpace(), param) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class ToUpperFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "h2o.toupper")
    self.arg_subspaces = (StringDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample()]))

class TransposeFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "t")
    self.arg_subspaces = (RealDataArgSpace(),) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample()]))

class MMFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None):
    FeatureSpace.__init__(self, "%*%")
    data1 = RealDataArgSpace(name="x")
    data1.cols_set = [8]
    data1.rows_set = [10]
    data2 = RealDataArgSpace(name="y")
    data2.cols_set = [10]
    data2.rows_set = [8]
    self.arg_subspaces = (data1, data2) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample()]))

class VarFeatureSpace(FeatureSpace):
  def __init__(self, arg_subspaces=None, na=True):
    FeatureSpace.__init__(self, "h2o.var")
    data2 = RealDataArgSpace(name="y", na=na)
    data2.null = True
    self.arg_subspaces = (RealDataArgSpace(na=na),
                          data2,
                          LogicalParameterArgSpace(name="na.rm"),
                          VarUseParameterArgSpace(na=na)) if arg_subspaces is None else arg_subspaces

  def sample(self):
    return FeatureSpaceSample(self.name,
                                FeatureSpace.combine_subspace_samplings(self, [self.arg_subspaces[0].sample(),
                                                                               self.arg_subspaces[1].sample(),
                                                                               self.arg_subspaces[2].sample(),
                                                                               self.arg_subspaces[3].sample()]))