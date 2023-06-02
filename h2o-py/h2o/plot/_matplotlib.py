
def get_matplotlib_pyplot(server, raise_if_not_available=False):
    try:
        # noinspection PyUnresolvedReferences
        import matplotlib
        from distutils.version import LooseVersion
        if server:
            if LooseVersion(matplotlib.__version__) <= LooseVersion("3.1"):
                matplotlib.use("Agg", warn=False)
            else:  # Versions >= 3.2 don't have warn argument
                matplotlib.use("Agg")
        try:
            # noinspection PyUnresolvedReferences
            import matplotlib.pyplot as plt
        except ImportError as e:
            if server:
                raise e
            import warnings
            # Possibly failed due to missing tkinter in old matplotlib in python 2.7
            warnings.warn(
                "An error occurred while importing matplotlib with backend \"{}\". Trying again with Agg backend."
                    .format(matplotlib.get_backend()))
            plt = get_matplotlib_pyplot(True, raise_if_not_available)
        return plt
    except ImportError as e:
        if raise_if_not_available:
            raise e
        print("`matplotlib` library is required for this function!")
        return None


def get_polycollection(server, raise_if_not_available=False):
    try:
        from matplotlib.collections import PolyCollection as polycoll
        return polycoll
    except ImportError as e:
        if raise_if_not_available:
            raise e
        print("`matplotlib` library is required for this function!")
        return None
    
    
def get_matplotlib_cm(function_name):
    try:
        from matplotlib import cm
        return cm
    except ImportError:
        print('matplotlib library is required for 3D plots for function {0}'.format(function_name))
        return None


def get_mplot3d_axes(function_name):
    try:
        # noinspection PyUnresolvedReferences
        from mpl_toolkits.mplot3d import Axes3D
        return Axes3D
    except ImportError:
        print("`mpl_toolkits.mplot3d` library is required for function {0}!".format(function_name))
        return None


