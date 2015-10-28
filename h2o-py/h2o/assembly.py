from collections import namedtuple
import uuid, urllib2
from h2o import H2OConnection, _quoted, get_frame, H2OFrame


class H2OAssembly:
  """Extension class of Pipeline implementing additional methods:

    * to_pojo: Exports the assembly to a self-contained Java POJO used in a per-row, high-throughput environment.
    * union: Combine two H2OAssembly objects, the resulting row from each H2OAssembly are joined with simple concatenation.
  """

  # static properties pointing to H2OFrame methods
  divide = H2OFrame.__div__
  plus   = H2OFrame.__add__
  multiply= H2OFrame.__mul__
  minus = H2OFrame.__sub__
  less_than = H2OFrame.__lt__
  less_than_equal = H2OFrame.__le__
  equal_equal = H2OFrame.__eq__
  not_equal = H2OFrame.__ne__
  greater_than = H2OFrame.__gt__
  greater_than_equal = H2OFrame.__ge__

  def __init__(self, steps):
    """
    Build a new H2OAssembly.

    :param steps: A list of steps that sequentially transforms the input data.
    :return: An H2OFrame.
    """
    self.id = None
    self.steps = steps
    self.fuzed = []
    self.in_colnames = None
    self.out_colnames = None

  @property
  def names(self):
    return zip(*self.steps)[0][:-1]

  def to_pojo(self, pojo_name="", path="", get_jar=True):
    if pojo_name=="": pojo_name = unicode("AssemblyPOJO_" + str(uuid.uuid4()))
    java = H2OConnection.get("Assembly.java/" + self.id + "/" + pojo_name, _rest_version=99)
    file_path = path + "/" + pojo_name + ".java"
    if path == "": print java.text
    else:
      with open(file_path, 'wb') as f:
        f.write(java.text)
    if get_jar and path!="":
      url = H2OConnection.make_url("h2o-genmodel.jar")
      filename = path + "/" + "h2o-genmodel.jar"
      response = urllib2.urlopen(url)
      with open(filename, "wb") as f:
        f.write(response.read())

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
    res = "[" + ",".join([_quoted(r.replace('"',"'")) for r in res]) + "]"
    j = H2OConnection.post_json(url_suffix="Assembly", steps=res, frame=fr.frame_id, _rest_version=99)
    self.id = j["assembly"]["name"]
    return get_frame(j["result"]["name"])

class H2OCol:
  """
  Wrapper class for H2OBinaryOp step's left/right args.

  Use if you want to signal that a column actually comes from the train to be fitted on.
  """
  def __init__(self, column):
    self.col = column
