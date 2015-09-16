from collections import namedtuple
from h2o import H2OConnection, _quoted, get_frame


class H2OAssembly:
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
    self.steps = steps
    self.fuzed = []
    self.in_colnames = None
    self.out_colnames = None

  @property
  def names(self):
    return zip(*self.steps)[0][:-1]

  def to_pojo(self,pojo_name=None):
    pass

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

  def fit(self, fr, **fit_params):
    res = []
    for step in self.steps:
      res.append(step[1].to_rest(step[0]))
    res = ",".join([_quoted(r.replace('"',"'")) for r in res])
    res = "[" + res + "]"
    j = H2OConnection.post_json(url_suffix="Assembly", assembly=res, frame=fr._id, _rest_version=99)
    return get_frame(j["result"])
