# -*- encoding: utf-8 -*-
from h2o.utils.compatibility import *  # NOQA

import os
import uuid

import h2o
from h2o.frame import H2OFrame
from h2o.transforms.transform_base import H2OTransformer
from h2o.utils.shared_utils import quoted
from h2o.utils.typechecks import assert_is_type


class H2OAssembly(object):
    """
    The H2OAssembly class can be used to specify multiple frame operations in one place.

    :returns: a new H2OFrame.

    :example:

    >>> iris = h2o.load_dataset("iris")
    >>> from h2o.assembly import *
    >>> from h2o.transforms.preprocessing import *
    >>> assembly = H2OAssembly(steps=[("col_select",
    ...                                H2OColSelect(["Sepal.Length", "Petal.Length", "Species"])),
    ...                               ("cos_Sepal.Length",
    ...                                H2OColOp(op=H2OFrame.cos, col="Sepal.Length", inplace=True)),
    ...                               ("str_cnt_Species",
    ...                                H2OColOp(op=H2OFrame.countmatches,
    ...                                col="Species",
    ...                                inplace=False, pattern="s"))])
    >>> result = assembly.fit(iris)  # fit the assembly and perform the munging operations
    >>> result
     Sepal.Length    Petal.Length     Species     Species0
    --------------  --------------  -----------  ----------
       0.377978           1.4       Iris-setosa       3
       0.186512           1.4       Iris-setosa       3
      -0.0123887          1.3       Iris-setosa       3
      -0.112153           1.5       Iris-setosa       3
       0.283662           1.4       Iris-setosa       3
       0.634693           1.7       Iris-setosa       3
      -0.112153           1.4       Iris-setosa       3
       0.283662           1.5       Iris-setosa       3
      -0.307333           1.4       Iris-setosa       3
       0.186512           1.5       Iris-setosa       3
    [150 rows x 4 columns]


    In this example, we first load the iris frame. Next, the following data munging operations are performed on the
    iris frame:

        1. select only three out of the five columns;
        2. take the cosine of the column "Sepal.Length" and replace the original column with the cosine of the column;
        3. count the number of rows with the letter "s" in the class column. Since ``inplace=False``,
           a new column is generated to hold the result.

    Extension class of Pipeline implementing additional methods:

       - ``to_pojo``: Exports the assembly to a self-contained Java POJO used in a per-row, high-throughput environment.

    Additionally, H2OAssembly provides a few static methods that perform element to element operations between
    two frames. They all are called as:

    >>> H2OAssembly.op(frame1, frame2)

    where ``frame1, frame2`` are H2OFrames of the same size and same column types. It will return an H2OFrame
    containing the element-wise result of operation op. The following operations are currently supported:

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
    """
    Divides one frame from the other.

    :returns: the quotient of the frames.
    
    :examples: 

    >>> python_list1 = [[4,4,4,4],[4,4,4,4]]
    >>> python_list2 = [[2,2,2,2], [2,2,2,2]]
    >>> frame1 = h2o.H2OFrame(python_obj=python_list1)
    >>> frame2 = h2o.H2OFrame(python_obj=python_list2)
    >>> H2OAssembly.divide(frame1, frame2)
       C1    C2    C3    C4
      ----  ----  ----  ----
       2     2     2     2
       2     2     2     2
    """
    plus = H2OFrame.__add__
    """
    Adds the frames together.

    :returns: the sum of the frames.

    :examples:

    >>> python_list1 = [[4,4,4,4],[4,4,4,4]]
    >>> python_list2 = [[2,2,2,2], [2,2,2,2]]
    >>> frame1 = h2o.H2OFrame(python_obj=python_list1)
    >>> frame2 = h2o.H2OFrame(python_obj=python_list2)
    >>> H2OAssembly.plus(frame1, frame2)
         C1    C2    C3    C4
        ----  ----  ----  ----
         6     6     6     6
         6     6     6     6
    """
    multiply = H2OFrame.__mul__
    """
    Multiplies the frames together.

    :returns: the product of the frames. 
    
    :examples:

    >>> python_list1 = [[4,4,4,4],[4,4,4,4]]
    >>> python_list2 = [[2,2,2,2], [2,2,2,2]]
    >>> frame1 = h2o.H2OFrame(python_obj=python_list1)
    >>> frame2 = h2o.H2OFrame(python_obj=python_list2)
    >>> H2OAssembly.multiply(frame1, frame2)
        C1    C2    C3    C4
       ----  ----  ----  ----
        8     8     8     8
        8     8     8     8
    """
    minus = H2OFrame.__sub__
    """
    Subtracts one frame from the other.

    :examples: the difference of the frames.

    >>> python_list1 = [[4,4,4,4],[4,4,4,4]]
    >>> python_list2 = [[2,2,2,2], [2,2,2,2]]
    >>> frame1 = h2o.H2OFrame(python_obj=python_list1)
    >>> frame2 = h2o.H2OFrame(python_obj=python_list2)
    >>> H2OAssembly.minus(frame1, frame2)
        C1    C2    C3    C4
       ----  ----  ----  ----
        2     2     2     2
        2     2     2     2
    """
    less_than = H2OFrame.__lt__
    """
    Measures whether one frame is less than the other.  

    :returns: boolean true/false response (0/1 = no/yes).

    :examples: 

    >>> python_list1 = [[4,4,4,4],[4,4,4,4]]
    >>> python_list2 = [[2,2,2,2], [2,2,2,2]]
    >>> frame1 = h2o.H2OFrame(python_obj=python_list1)
    >>> frame2 = h2o.H2OFrame(python_obj=python_list2)
    >>> H2OAssembly.less_than(frame1, frame2)
         C1    C2    C3    C4
        ----  ----  ----  ----
          0     0     0     0
          0     0     0     0
    """
    less_than_equal = H2OFrame.__le__
    """
    Measures whether one frame is less than or equal to the other.
    
    :returns: boolean true/false response (0/1 = no/yes).

    :examples:

    >>> python_list1 = [[4,4,4,4],[4,4,4,4]]
    >>> python_list2 = [[2,2,2,2], [2,2,2,2]]
    >>> frame1 = h2o.H2OFrame(python_obj=python_list1)
    >>> frame2 = h2o.H2OFrame(python_obj=python_list2)
    >>> H2OAssembly.less_than_equal(frame1, frame2)
         C1    C2    C3    C4
        ----  ----  ----  ----
         0     0     0     0
         0     0     0     0
    """
    equal_equal = H2OFrame.__eq__
    """
    Measures whether the frames are equal. 

    :returns: boolean true/false response (0/1 = no/yes).

    :examples:

    >>> python_list1 = [[4,4,4,4],[4,4,4,4]]
    >>> python_list2 = [[2,2,2,2], [2,2,2,2]]
    >>> frame1 = h2o.H2OFrame(python_obj=python_list1)
    >>> frame2 = h2o.H2OFrame(python_obj=python_list2)
    >>> H2OAssembly.equal_equal(frame1, frame2)
        C1    C2    C3    C4
       ----  ----  ----  ----
         0     0     0     0
         0     0     0     0
    """
    not_equal = H2OFrame.__ne__
    """
    Measures whether the frames are not equal.

    :returns: boolean true/false response (0/1 = no/yes).
    
    :examples:

    >>> python_list1 = [[4,4,4,4],[4,4,4,4]]
    >>> python_list2 = [[2,2,2,2], [2,2,2,2]]
    >>> frame1 = h2o.H2OFrame(python_obj=python_list1)
    >>> frame2 = h2o.H2OFrame(python_obj=python_list2)
    >>> H2OAssembly.not_equal(frame1, frame2)
        C1    C2    C3    C4
       ----  ----  ----  ----
         1     1     1     1
         1     1     1     1
    """
    greater_than = H2OFrame.__gt__
    """
    Measures whether one frame is greater than the other.

    :returns: boolean true/false response (0/1 = no/yes).

    :examples:

    >>> python_list1 = [[4,4,4,4],[4,4,4,4]]
    >>> python_list2 = [[2,2,2,2], [2,2,2,2]]
    >>> frame1 = h2o.H2OFrame(python_obj=python_list1)
    >>> frame2 = h2o.H2OFrame(python_obj=python_list2)
    >>> H2OAssembly.greater_than(frame1, frame2)
        C1    C2    C3    C4
       ----  ----  ----  ----
         1     1     1     1
         1     1     1     1
    """
    greater_than_equal = H2OFrame.__ge__
    """
    Measures whether one frame is greater than or equal to the other.

    :returns: boolean true/false response (0/1 = no/yes).

    :examples:

    >>> python_list1 = [[4,4,4,4],[4,4,4,4]]
    >>> python_list2 = [[2,2,2,2], [2,2,2,2]]
    >>> frame1 = h2o.H2OFrame(python_obj=python_list1)
    >>> frame2 = h2o.H2OFrame(python_obj=python_list2)
    >>> H2OAssembly.greater_than_equal(frame1, frame2)
         C1    C2    C3    C4
        ----  ----  ----  ----
         1     1     1     1
         1     1     1     1
    """


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
        """
        Gives the column names.

        :returns: the specified column names.

        :examples:

        >>> iris = h2o.load_dataset("iris")
        >>> from h2o.assembly import *
        >>> from h2o.transforms.preprocessing import *
        >>> assembly = H2OAssembly(steps=[("col_select",
        ...                                H2OColSelect(["Sepal.Length", "Petal.Length", "Species"])),
        ...                               ("cos_Sepal.Length",
        ...                                H2OColOp(op=H2OFrame.cos, col="Sepal.Length", inplace=True)),
        ...                               ("str_cnt_Species",
        ...                                H2OColOp(op=H2OFrame.countmatches,
        ...                                col="Species",
        ...                                inplace=False, pattern="s"))])
        >>> result = assembly.fit(iris)
        >>> result.names
        [u'Sepal.Length', u'Petal.Length', u'Species', u'Species0']
        """
        return list(zip(*self.steps))[0][:-1]


    def download_mojo(self, file_name="", path="."):
        """
        Convert the munging operations performed on H2OFrame into a MOJO 2 artifact. This method requires an additional
        mojo2-runtime library on the Java classpath. The library can be found at this maven URL::
        https://repo1.maven.org/maven2/ai/h2o/mojo2-runtime/2.7.11.1/mojo2-runtime-2.7.11.1.jar.
        
        The library can be added to the classpath via Java command when starting an H2O node from the command line::
        
            java -cp <path_to_h2o_jar>:<path_to_mojo2-runtime_library> water.H2OApp
          
        The library can also be added to the Java classpath from Python while starting an H2O cluster
        via ``h2o.init()``::
      
        >>> import h2o
        >>> h2o.init(extra_classpath = ["<path_to_mojo2-runtime_library>"]) 
        
        The MOJO 2 artifact created by this method can be utilized according to the tutorials on the page
        https://docs.h2o.ai/driverless-ai/1-10-lts/docs/userguide/scoring-mojo-scoring-pipeline.html with one additional
        requirement. The artifact produced by this method requires 
        `h2o-genmodel.jar <https://mvnrepository.com/artifact/ai.h2o/h2o-genmodel>`_ to be present on Java classpath.  
        
        :param file_name:  (str) Name of MOJO 2 artifact.
        :param path:  (str) Local Path on a user side  serving as target for  MOJO 2 artifact.
        :return: Streamed file.
        
        :examples:

        >>> from h2o.assembly import *
        >>> from h2o.transforms.preprocessing import *
        >>> iris = h2o.load_dataset("iris")
        >>> assembly = H2OAssembly(steps=[("col_select",
        ...                                H2OColSelect(["Sepal.Length",
        ...                                "Petal.Length", "Species"])),
        ...                               ("cos_Sepal.Length",
        ...                                H2OColOp(op=H2OFrame.cos,
        ...                                col="Sepal.Length", inplace=True)),
        ...                               ("str_cnt_Species",
        ...                                H2OColOp(op=H2OFrame.countmatches,
        ...                                col="Species", inplace=False,
        ...                                pattern="s"))])
        >>> result = assembly.fit(iris)
        >>> assembly.download_mojo(file_name="iris_mojo", path='')
        
        .. note::
            The output column names of the created MOJO 2 pipeline are prefixed with "assembly\_"  since the
            MOJO2 library requires unique names across all columns present in pipeline.
        
        """
        assert_is_type(file_name, str)
        assert_is_type(path, str)
        if file_name == "":
            file_name = "AssemblyMOJO_" + str(uuid.uuid4())
        return h2o.api("GET /99/Assembly.fetch_mojo_pipeline/%s/%s" % (self.id, file_name), save_to=path)


    def to_pojo(self, pojo_name="", path="", get_jar=True):
        """
        Convert the munging operations performed on H2OFrame into a POJO.

        :param pojo_name:  (str) Name of POJO.
        :param path:  (str) path of POJO.
        :param get_jar: (bool) Whether to also download the h2o-genmodel.jar file needed to compile the POJO.
        :return: None.

        :examples:

        >>> from h2o.assembly import *
        >>> from h2o.transforms.preprocessing import *
        >>> iris = h2o.load_dataset("iris")
        >>> assembly = H2OAssembly(steps=[("col_select",
        ...                                H2OColSelect(["Sepal.Length",
        ...                                "Petal.Length", "Species"])),
        ...                               ("cos_Sepal.Length",
        ...                                H2OColOp(op=H2OFrame.cos,
        ...                                col="Sepal.Length", inplace=True)),
        ...                               ("str_cnt_Species",
        ...                                H2OColOp(op=H2OFrame.countmatches,
        ...                                col="Species", inplace=False,
        ...                                pattern="s"))])
        >>> result = assembly.fit(iris)
        >>> assembly.to_pojo(pojo_name="iris_pojo", path='', get_jar=False)
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
        To perform the munging operations on a frame specified in steps on the frame ``fr``.

        :param fr: H2OFrame where munging operations are to be performed on.
        :return: H2OFrame after munging operations are completed.

        :examples:

        >>> iris = h2o.load_dataset("iris")
        >>> assembly = H2OAssembly(steps=[("col_select",
        ...                        H2OColSelect(["Sepal.Length",
        ...                        "Petal.Length", "Species"])),
        ...                       ("cos_Sepal.Length",
        ...                        H2OColOp(op=H2OFrame.cos,
        ...                        col="Sepal.Length",
        ...                        inplace=True)),
        ...                       ("str_cnt_Species",
        ...                        H2OColOp(op=H2OFrame.countmatches,
        ...                        col="Species",
        ...                        inplace=False,
        ...                        pattern="s"))])
        >>> fit = assembly.fit(iris)
        >>> fit

        """
        assert_is_type(fr, H2OFrame)
        steps = "[%s]" % ",".join(quoted(step[1].to_rest(step[0]).replace('"', "'")) for step in self.steps)
        j = h2o.api("POST /99/Assembly", data={"steps": steps, "frame": fr.frame_id})
        self.id = j["assembly"]["name"]
        return H2OFrame.get_frame(j["result"]["name"])
