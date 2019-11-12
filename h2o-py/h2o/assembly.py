# -*- encoding: utf-8 -*-
from __future__ import division, print_function, absolute_import, unicode_literals

import uuid

import h2o
from h2o.frame import H2OFrame
from h2o.transforms.transform_base import H2OTransformer
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.shared_utils import urlopen, quoted
from h2o.utils.typechecks import assert_is_type
import os


class H2OAssembly(object):
    """
    H2OAssembly class can be used to specify multiple frame operations in one place.

    Sample usage:

    >>> iris = h2o.load_dataset("iris")
    >>> assembly = H2OAssembly(steps=[
    ... ("col_select",       H2OColSelect(["Sepal.Length", "Petal.Length", "Species"])),
    ... ("cos_Sepal.Length", H2OColOp(op=H2OFrame.cos, col="Sepal.Length", inplace=True)),
    ... ("str_cnt_Species",  H2OColOp(op=H2OFrame.countmatches, col="Species",
    ...                               inplace=False, pattern="s"))
    ... ])
    >>> result = assembly.fit(iris)  # fit the assembly and perform the munging operations

    In this example, we first load the iris frame.  Next, the following data munging operations are performed on the
    iris frame

        1. only select three columns out of the five columns;
        2. take the cosine of the column Sepal.Length and replace the original column with the cosine of the column;
        3. want to count the number of rows with the letter s in the class column.  Since inplace is set to
           False, a new column is generated to hold the result.

    Extension class of Pipeline implementing additional methods:

       - to_pojo: Exports the assembly to a self-contained Java POJO used in a per-row, high-throughput environment.

    In addition, H2OAssembly provides a few static methods that perform element to element operations between
    two frames. They all are called as

    >>> H2OAssembly.op(frame1, frame2)

    while frame1, frame2 are H2OFrame of the same size and same column types.  It will return a H2OFrame
    containing the element-wise result of operation op.  The following operations are currently supported

        - divide
        - plus
        - multiply
        - minus
        - less_than
        - less_than_equal
        - equal_equal
        - not_equal
        - greater_than
        - greater_than_equal
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

        :param steps: A list of steps that sequentially transforms the input data. Each step is a
            tuple ``(name, operation)``, where each ``operation`` is an instance of an ``H2OTransformer`` class.
        """
        assert_is_type(steps, [(str, H2OTransformer)])
        self.id = None
        self.steps = steps
        self.fuzed = []
        self.in_colnames = None
        self.out_colnames = None


    @property
    def names(self):
        return list(zip(*self.steps))[0][:-1]


    def to_pojo(self, pojo_name="", path="", get_jar=True):
        """
        Convert the munging operations performed on H2OFrame into a POJO.

        :param pojo_name:  (str) Name of POJO
        :param path:  (str) path of POJO.
        :param get_jar: (bool) Whether to also download the h2o-genmodel.jar file needed to compile the POJO
        :return: None
        """
        assert_is_type(pojo_name, str)
        assert_is_type(path, str)
        assert_is_type(get_jar, bool)
        if pojo_name == "":
            pojo_name = "AssemblyPOJO_" + str(uuid.uuid4())
        java = h2o.api("GET /99/Assembly.java/%s/%s" % (self.id, pojo_name))
        file_path = path + "/" + pojo_name + ".java"
        if path == "":
            print(java)
        else:
            with open(file_path, 'w', encoding="utf-8") as f:
                f.write(java)  # this had better be utf-8 ?
        if get_jar and path != "":
            h2o.api("GET /3/h2o-genmodel.jar", save_to=os.path.join(path, "h2o-genmodel.jar"))

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


    def fit(self, fr):
        """
        To perform the munging operations on a frame specified in steps on the frame fr.

        :param fr: H2OFrame where munging operations are to be performed on.
        :return: H2OFrame after munging operations are completed.
        """
        assert_is_type(fr, H2OFrame)
        steps = "[%s]" % ",".join(quoted(step[1].to_rest(step[0]).replace('"', "'")) for step in self.steps)
        j = h2o.api("POST /99/Assembly", data={"steps": steps, "frame": fr.frame_id})
        self.id = j["assembly"]["name"]
        return H2OFrame.get_frame(j["result"]["name"])
