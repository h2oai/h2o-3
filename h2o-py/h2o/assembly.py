from sklearn.pipeline import Pipeline
from estimators.estimator_base import H2OEstimator
from collections import namedtuple


class DummyEstimator(H2OEstimator):
  pass


class H2OAssembly(Pipeline):
  """
  Extension class of Pipeline implementing additional methods:

    * to_pojo: Exports the assembly to a self-contained Java POJO used in a per-row, high-throughput environment.
    * fuse: Combine two H2OAssembly objects, the resulting row from each H2OAssembly are joined with simple concatenation.
  """
  def __init__(self, steps):
    """
    Build a new H2OAssembly.

    :param steps: A list of steps that sequentially transforms the input data.
    :return: An H2OFrame.
    """
    steps.append(("dummy",DummyEstimator()))  # work-around for sklearn Pipeline demanding estimator cap.
    super(H2OAssembly, self).__init__(steps)
    self.fuzed = []
    self.in_colnames = None
    self.out_colnames = None

  @property
  def names(self):
    return zip(*self.steps)[0][:-1]

  def to_pojo(self,pojo_name=None):
    # build a java pojo
    # gen classes, then gen steps
    if pojo_name is None:
      pojo_name = "GeneratedMungingPojo"

    classes=[]
    steps=[]
    init_steps = ""
    for i,name in enumerate(self.names):
      step = self.named_steps[name]
      classes.append(step.gen_class(name))
      init_steps += """
          _steps[%s] = %s""" % (i, step.gen_step(name))
    classes = "\n".join(classes)
    return """
    class %s extends GenMunger {

        public %s() {
          _steps = new Step[%s];
          %s
        }
      %s
    }
    """ % (pojo_name, pojo_name, len(self.steps)-1, init_steps, classes)


  def union(self, assemblies):
    # fuse the assemblies onto this one, each is added to the end going left -> right
    # assemblies must be a list of namedtuples.
    #   [(H2OAssembly, X, y, {params}), ..., (H2OAssembly, X, y, {params})]
    for i in assemblies:
      if not isinstance(i, namedtuple):
        raise ValueError("Not a namedtuple. Assembly must be of type collections.namedtuple with fields [assembly, x, params].")
      if i._fields != ('assembly','x','params'):
        raise ValueError("Assembly must be a namedtuple with fields ('assembly', 'x', 'params').")
      self.fuzed.append(i)

  def fit(self, X, **fit_params):
    self.in_colnames = X.names
    rez = [self._pre_transform(X,None,**fit_params)[0]]
    for i in self.fuzed:
      rez.append(i.assembly._pre_transform(i.x,None,i.params)[0])
    result = reduce(lambda x,y: x.cbind(y), rez)
    self.out_colnames = result.names
    return result