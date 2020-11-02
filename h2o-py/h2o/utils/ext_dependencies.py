
def get_matplotlib_pyplot(server):
    try:
        # noinspection PyUnresolvedReferences
        import matplotlib
        from distutils.version import LooseVersion
        if server:
            if LooseVersion(matplotlib.__version__) <= LooseVersion("3.1"):
                matplotlib.use("Agg", warn=False)
            else:  # Versions >= 3.2 don't have warn argument
                matplotlib.use("Agg")
        # noinspection PyUnresolvedReferences
        import matplotlib.pyplot as plt
        return plt
    except ImportError:
        print("`matplotlib` library is required for this function!")
        return None


