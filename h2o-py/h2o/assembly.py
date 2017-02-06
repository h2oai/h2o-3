# -*- encoding: utf-8 -*-
from __future__ import division, print_function, absolute_import, unicode_literals

import uuid

import h2o
from h2o.frame import H2OFrame
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.shared_utils import urlopen, quoted


class H2OAssembly(object):
    """
    Extension class of Pipeline implementing additional methods:

      - to_pojo: Exports the assembly to a self-contained Java POJO used in a per-row, high-throughput environment.
      - union: Combine two H2OAssembly objects, the resulting row from each H2OAssembly are joined with simple
        concatenation.
    """

    # static properties pointing to H2OFrame methods
    divide = H2OFrame.__truediv__
    plus = H2OFrame.__add__
    multiply = H2OFrame.__mul__
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

        :returns: H2OFrame
        """
        self.id = None
        self.steps = steps
        self.fuzed = []
        self.in_colnames = None
        self.out_colnames = None


    @property
    def names(self):
        return list(zip(*self.steps))[0][:-1]


    def to_pojo(self, pojo_name="", path="", get_jar=True):
        if pojo_name == "": pojo_name = "AssemblyPOJO_" + str(uuid.uuid4())
        java = h2o.api("GET /99/Assembly.java/%s/%s" % (self.id, pojo_name))
        file_path = path + "/" + pojo_name + ".java"
        if path == "":
            print(java)
        else:
            with open(file_path, 'w', encoding="utf-8") as f:
                f.write(java)  # this had better be utf-8 ?
        if get_jar and path != "":
            url = h2o.connection().make_url("h2o-genmodel.jar")
            filename = path + "/" + "h2o-genmodel.jar"
            response = urlopen()(url)
            with open(filename, "wb") as f:
                f.write(response.read())


    # def union(self, assemblies):
    #   # fuse the assemblies onto this one, each is added to the end going left -> right
    #   # assemblies must be a list of namedtuples.
    #   #   [(H2OAssembly, X, y, {params}), ..., (H2OAssembly, X, y, {params})]
    #   for i in assemblies:
    #     if not isinstance(i, namedtuple):
    #       raise ValueError("Not a namedtuple. Assembly must be of type collections.namedtuple with fields [assembly, x, params].")
    #     if i._fields != ('assembly','x','params'):
    #       raise ValueError("Assembly must be a namedtuple with fields ('assembly', 'x', 'params').")
    #     self.fuzed.append(i)


    def fit(self, fr, **fit_params):
        res = []
        for step in self.steps:
            res.append(step[1].to_rest(step[0]))
        res = "[" + ",".join([quoted(r.replace('"', "'")) for r in res]) + "]"
        j = h2o.api("POST /99/Assembly", data={"steps": res, "frame": fr.frame_id})
        self.id = j["assembly"]["name"]
        return H2OFrame.get_frame(j["result"]["name"])




class H2OCol(object):
    """
    Wrapper class for H2OBinaryOp step's left/right args.

    Use if you want to signal that a column actually comes from the train to be fitted on.
    """

    def __init__(self, column):
        self.col = column

        # TODO: handle arbitrary (non H2OFrame) inputs -- sql, web, file, generated
