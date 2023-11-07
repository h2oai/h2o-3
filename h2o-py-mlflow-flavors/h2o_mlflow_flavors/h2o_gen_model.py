"""
The `h2o_mlflow_flavors.h2o_gen_model` module provides an API for working with H2O MOJO and POJO models.
"""

import logging
import os
import tempfile
import pandas
import subprocess
import sys

import yaml

import mlflow
from mlflow import pyfunc
from mlflow.models import Model
from mlflow.models.model import MLMODEL_FILE_NAME
from mlflow.models.utils import _save_example
from mlflow.models.signature import ModelSignature
from mlflow.models.utils import ModelInputExample
from mlflow.utils.environment import (
    _CONDA_ENV_FILE_NAME,
    _CONSTRAINTS_FILE_NAME,
    _PYTHON_ENV_FILE_NAME,
    _REQUIREMENTS_FILE_NAME,
    _mlflow_conda_env,
    _process_conda_env,
    _process_pip_requirements,
    _PythonEnv,
    _validate_env_arguments,
)

from mlflow.utils.file_utils import write_to
from mlflow.utils.model_utils import (
    _add_code_from_conf_to_system_path,
    _get_flavor_configuration,
    _validate_and_copy_code_paths,
    _validate_and_prepare_target_save_path,
)
from mlflow.utils.requirements_utils import _get_pinned_requirement
from mlflow.tracking.artifact_utils import _download_artifact_from_uri


_logger = logging.getLogger(__name__)

FLAVOR_NAME = "h2o_gen_model"


def get_default_pip_requirements():
    """
    :return: A list of default pip requirements for MLflow Models produced by this flavor.
             Calls to :func:`save_model()` and :func:`log_model()` produce a pip environment
             that, at minimum, contains these requirements.
    """
    return []


def get_default_conda_env():
    """
    :return: The default Conda environment for MLflow Models produced by calls to
             :func:`save_model()` and :func:`log_model()`.
    """
    return _mlflow_conda_env(additional_pip_deps=get_default_pip_requirements())


def get_params(h2o_model):
    return h2o_model.actual_params 
    
    
def get_metrics(h2o_model, metric_type=None):
    def get_metrics_section(output, prefix, metric_type):
        is_valid = lambda key, val: isinstance(val, (bool, float, int)) and not str(key).endswith("checksum")
        items = output[metric_type]._metric_json.items()
        dictionary = dict(items)
        if dictionary["custom_metric_name"] is None:
            del dictionary["custom_metric_value"]
        return {prefix + str(key): val for key, val in dictionary.items() if is_valid(key, val)}

    metric_type_lower = None
    if metric_type:
        metric_type_lower = metric_type.toLowerCase()    

    output = h2o_model._model_json["output"]
    metrics = {}

    if output["training_metrics"] and (metric_type_lower is None or metric_type_lower == "training"):
        training_metrics = get_metrics_section(output, "training_", "training_metrics")
        metrics = dict(metrics, **training_metrics)
    if output["validation_metrics"] and (metric_type_lower is None or metric_type_lower == "validation"):
        validation_metrics = get_metrics_section(output, "validation_", "validation_metrics")
        metrics = dict(metrics, **validation_metrics)
    if output["cross_validation_metrics"] and (metric_type_lower is None or metric_type_lower in ["cv", "cross_validation"]):
        cross_validation_metrics = get_metrics_section(output, "cv_", "cross_validation_metrics")
        metrics = dict(metrics, **cross_validation_metrics)
        
    return metrics


def save_model(
    h2o_model,
    path,
    conda_env=None,
    code_paths=None,
    mlflow_model=None,
    signature=None,
    input_example=None,
    pip_requirements=None,
    extra_pip_requirements=None,
    model_type="MOJO"
):
    import h2o

    model_type_upper = model_type.upper()
    if model_type_upper != "MOJO" and model_type_upper != "POJO":
        raise ValueError(f"The `model_type` parameter must be 'MOJO' or 'POJO'. The passed value was '{model_type}'.")
    
    _validate_env_arguments(conda_env, pip_requirements, extra_pip_requirements)
    _validate_and_prepare_target_save_path(path)
    code_dir_subpath = _validate_and_copy_code_paths(code_paths, path)

    if mlflow_model is None:
        mlflow_model = Model()
    if signature is not None:
        mlflow_model.signature = signature
    if input_example is not None:
        _save_example(mlflow_model, input_example, path)

    if model_type_upper == "MOJO":
        model_data_path = h2o_model.download_mojo(path=path, get_genmodel_jar=True)
        model_file = os.path.basename(model_data_path)
    else:
        model_data_path = h2o_model.download_pojo(path=path, get_genmodel_jar=True)
        h2o_genmodel_jar = os.path.join(path, "h2o-genmodel.jar")
        output_path = os.path.join(path, "classes")
        javac_cmd = ["javac", "-cp", h2o_genmodel_jar, "-d", output_path, "-J-Xmx12g", model_data_path]
        subprocess.check_call(javac_cmd)
        model_file = os.path.basename(model_data_path).replace(".java", "")
    
    pyfunc.add_to_model(
        mlflow_model,
        loader_module="h2o_mlflow_flavors.h2o_gen_model",
        model_path=model_file,
        conda_env=_CONDA_ENV_FILE_NAME,
        python_env=_PYTHON_ENV_FILE_NAME,
        code=code_dir_subpath,
    )

    mlflow_model.add_flavor(
        FLAVOR_NAME,
        model_file=model_file,
        model_type=model_type_upper,
        h2o_version=h2o.__version__,
        code=code_dir_subpath,
    )
    mlflow_model.save(os.path.join(path, MLMODEL_FILE_NAME))

    if conda_env is None:
        if pip_requirements is None:
            default_reqs = get_default_pip_requirements()
            inferred_reqs = mlflow.models.infer_pip_requirements(
                path, FLAVOR_NAME, fallback=default_reqs
            )
            default_reqs = sorted(set(inferred_reqs).union(default_reqs))
        else:
            default_reqs = None
        conda_env, pip_requirements, pip_constraints = _process_pip_requirements(
            default_reqs, pip_requirements, extra_pip_requirements
        )
    else:
        conda_env, pip_requirements, pip_constraints = _process_conda_env(conda_env)

    with open(os.path.join(path, _CONDA_ENV_FILE_NAME), "w") as f:
        yaml.safe_dump(conda_env, stream=f, default_flow_style=False)

    if pip_constraints:
        write_to(os.path.join(path, _CONSTRAINTS_FILE_NAME), "\n".join(pip_constraints))

    write_to(os.path.join(path, _REQUIREMENTS_FILE_NAME), "\n".join(pip_requirements))

    _PythonEnv.current().to_yaml(os.path.join(path, _PYTHON_ENV_FILE_NAME))


def log_model(
    h2o_model,
    artifact_path,
    conda_env=None,
    code_paths=None,
    registered_model_name=None,
    signature: ModelSignature = None,
    input_example: ModelInputExample = None,
    pip_requirements=None,
    extra_pip_requirements=None,
    metadata=None,
    model_type="MOJO",
    **kwargs,
):
    """
    Log an H2O model as an MLflow artifact for the current run.

    :param h2o_model: H2O model to be saved.
    :param artifact_path: Run-relative artifact path.
    :param conda_env: {{ conda_env }}
    :param code_paths: A list of local filesystem paths to Python file dependencies (or directories
                       containing file dependencies). These files are *prepended* to the system
                       path when the model is loaded.
    :param registered_model_name: If given, create a model version under
                                  ``registered_model_name``, also creating a registered model if one
                                  with the given name does not exist.

    :param signature: {{ signature }}
    :param input_example: {{ input_example }}
    :param pip_requirements: {{ pip_requirements }}
    :param extra_pip_requirements: {{ extra_pip_requirements }}
    :param metadata: Custom metadata dictionary passed to the model and stored in the MLmodel file.

                     .. Note:: Experimental: This parameter may change or be removed in a future
                                             release without warning.
    :param model_type: A flag deciding whether the model is MOJO or POJO.                                            
    :param kwargs: kwargs to pass to ``h2o.save_model`` method.
    :return: A :py:class:`ModelInfo <mlflow.models.model.ModelInfo>` instance that contains the
             metadata of the logged model.
    """
    import h2o_mlflow_flavors
    return Model.log(
        artifact_path=artifact_path,
        flavor=h2o_mlflow_flavors.h2o_gen_model,
        registered_model_name=registered_model_name,
        h2o_model=h2o_model,
        conda_env=conda_env,
        code_paths=code_paths,
        signature=signature,
        input_example=input_example,
        pip_requirements=pip_requirements,
        extra_pip_requirements=extra_pip_requirements,
        model_type=model_type,
        metadata=metadata,
        **kwargs,
    )


def load_model(model_uri, dst_path=None):
    path = _download_artifact_from_uri(
        artifact_uri=model_uri, output_path=dst_path
    )
    return _load_model(path)


def _load_model(path):
    flavor_conf = _get_flavor_configuration(model_path=path, flavor_name=FLAVOR_NAME)
    model_type = flavor_conf["model_type"]
    model_file = flavor_conf["model_file"]

    return _H2OModelWrapper(model_file, model_type, path)


class _H2OModelWrapper:
    def __init__(self, model_file, model_type, path):
        self.model_file = model_file
        self.model_type = model_type
        self.path = path
        self.genmodel_jar_path = os.path.join(path, "h2o-genmodel.jar")

    def predict(self, dataframe, params=None):
        """
        :param dataframe: Model input data.
        :param params: Additional parameters to pass to the model for inference.

        :return: Model predictions.
        """
        with tempfile.TemporaryDirectory() as tempdir:
            input_file = os.path.join(tempdir, "input.csv")
            output_file = os.path.join(tempdir, "output.csv")
            separator = "`"
            import csv
            dataframe.to_csv(input_file, index=False, quoting=csv.QUOTE_NONNUMERIC, sep=separator)
            if self.model_type == "MOJO":
                class_path = self.genmodel_jar_path
                type_parameter = "--mojo"
                model_artefact = os.path.join(self.path, self.model_file)
            else:
                class_path_separator = ";" if sys.platform == "win32" else ":"
                class_path = self.genmodel_jar_path + class_path_separator + os.path.join(self.path, "classes")
                type_parameter = "--pojo"
                model_artefact = self.model_file.replace(".class", "")

            java_cmd = ["java", "-cp", class_path,
                        "-ea", "-Xmx12g", "-XX:ReservedCodeCacheSize=256m",
                        "hex.genmodel.tools.PredictCsv", "--separator", separator,
                        "--input", input_file, "--output", output_file, type_parameter, model_artefact, "--decimal"]
            ret = subprocess.call(java_cmd)
            assert ret == 0, "GenModel finished with return code %d." % ret
            predicted = pandas.read_csv(output_file)
            predicted.index = dataframe.index
            return predicted


def _load_pyfunc(path):
    """
    Load PyFunc implementation. Called by ``pyfunc.load_model``.

    :param path: Local filesystem path to the MLflow Model with the ``h2o`` flavor.
    """
    return _load_model(path)

    
