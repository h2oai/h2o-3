"""
The `h2o_mlflow_flavors.h2o_mojo` module provides an API for logging H2O DriverlessAI models. This models
exports H2O models in the following flavors:

h2o3

"""

import logging
import os
import pickle
import shutil
import uuid
import warnings

from typing import Any, Dict, Optional

import numpy as np
import pandas as pd
#import sktime
import yaml
import h2o
import random
import mlflow
import mlflow.h2o
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.generic import H2OGenericEstimator
from mlflow import pyfunc
from mlflow.exceptions import MlflowException
from mlflow.models import Model
from mlflow.models.model import MLMODEL_FILE_NAME
from mlflow.models.utils import _save_example
from mlflow.protos.databricks_pb2 import INTERNAL_ERROR, INVALID_PARAMETER_VALUE
from mlflow.models.signature import ModelSignature
from mlflow.models.signature import _infer_signature_from_input_example
from mlflow.models.utils import ModelInputExample
from mlflow.tracking._model_registry import DEFAULT_AWAIT_MAX_SLEEP_SECONDS
from mlflow.utils.docstring_utils import LOG_MODEL_PARAM_DOCS, format_docstring
from mlflow.tracking.artifact_utils import _download_artifact_from_uri
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

import h2o_mlflow_flavors
from h2o_mlflow_flavors.utils import match_file_from_name_pattern
from h2o_mlflow_flavors.utils import unzip_specific_file

from mlflow.utils.file_utils import write_to
from mlflow.utils.model_utils import (
    _add_code_from_conf_to_system_path,
    _get_flavor_configuration,
    _validate_and_copy_code_paths,
    _validate_and_prepare_target_save_path,
)
from mlflow.utils.requirements_utils import _get_pinned_requirement
#from sktime.utils.multiindex import flatten_multiindex
from pysparkling import *
_logger = logging.getLogger(__name__)


FLAVOR_NAME = "h2o==3.42.0.3"
H2O3_MODEL_INI = "model.ini"
MLFLOW_H2O3_MOJO_ARTIFACT = "mlflow/h2o_mojo"
MLFLOW_H2O3_MODEL_FILENAME = "h2o_mojo_model.zip"





def get_default_pip_requirements():
    """
    :return: A list of default pip requirements for MLflow Models produced by this flavor.
             Calls to :func:`save_model()` and :func:`log_model()` produce a pip environment
             that, at minimum, contains these requirements.
    """
    return [_get_pinned_requirement("h2o")]




def get_default_conda_env():
    """
    :return: The default Conda environment for MLflow Models produced by calls to
             :func:`save_model()` and :func:`log_model()`.
    """
    return _mlflow_conda_env(additional_pip_deps=get_default_pip_requirements())




#@format_docstring(LOG_MODEL_PARAM_DOCS.format(package_name=FLAVOR_NAME))
def save_model(
    h2o_model,
    path,
    conda_env=None,
    code_paths=None,
    mlflow_model=None,
    settings=None,
    signature: ModelSignature = None,
    input_example: ModelInputExample = None,
    pip_requirements=None,
    extra_pip_requirements=None,
    metadata=None,
    is_mojo = False
):
    """
    Save an H2O model to a path on the local file system.

    :param h2o_model: H2O model to be saved.
    :param path: Local path where the model is to be saved.
    :param conda_env: {{ conda_env }}
    :param code_paths: A list of local filesystem paths to Python file dependencies (or directories
                       containing file dependencies). These files are *prepended* to the system
                       path when the model is loaded.
    :param mlflow_model: :py:mod:`mlflow.models.Model` this flavor is being added to.
    :param signature: {{ signature }}
    :param input_example: {{ input_example }}
    :param pip_requirements: {{ pip_requirements }}
    :param extra_pip_requirements: {{ extra_pip_requirements }}
    :param metadata: Custom metadata dictionary passed to the model and stored in the MLmodel file.

                     .. Note:: Experimental: This parameter may change or be removed in a future
                                             release without warning.
    :param is_mojo: Can save a mojo directly as a h2o_model. If true, calls its own load model
    """
    import h2o
    if (is_mojo == True):
        h2o_model = mlflow.h2o_mojo.load_mojo_model(h2o_model)

    _validate_env_arguments(conda_env, pip_requirements, extra_pip_requirements)

    path = os.path.abspath(path)
    _validate_and_prepare_target_save_path(path)
    model_data_subpath = "model.h2o"
    model_data_path = os.path.join(path, model_data_subpath)
    os.makedirs(model_data_path)
    code_dir_subpath = _validate_and_copy_code_paths(code_paths, path)

    if signature is None and input_example is not None:
        wrapped_model = _H2OModelWrapper(h2o_model)
        signature = _infer_signature_from_input_example(input_example, wrapped_model)
    elif signature is False:
        signature = None

    if mlflow_model is None:
        mlflow_model = Model()
    if signature is not None:
        mlflow_model.signature = signature
    if input_example is not None:
        _save_example(mlflow_model, input_example, path)
    if metadata is not None:
        mlflow_model.metadata = metadata

    # Save h2o-model
    if hasattr(h2o, "download_model"):
        h2o_save_location = h2o.download_model(model=h2o_model, path=model_data_path)
    else:
        warnings.warn(
            "If your cluster is remote, H2O may not store the model correctly. "
            "Please upgrade H2O version to a newer version"
        )
        h2o_save_location = h2o.save_model(model=h2o_model, path=model_data_path, force=True)
    model_file = os.path.basename(h2o_save_location)

    # Save h2o-settings
    if settings is None:
        settings = {}
    settings["full_file"] = h2o_save_location
    settings["model_file"] = model_file
    settings["model_dir"] = model_data_path
    with open(os.path.join(model_data_path, "h2o.yaml"), "w") as settings_file:
        yaml.safe_dump(settings, stream=settings_file)

    pyfunc.add_to_model(
        mlflow_model,
        loader_module="mlflow.h2o",
        data=model_data_subpath,
        conda_env=_CONDA_ENV_FILE_NAME,
        python_env=_PYTHON_ENV_FILE_NAME,
        code=code_dir_subpath,
    )
    mlflow_model.add_flavor(
        FLAVOR_NAME, h2o_version=h2o.__version__, data=model_data_subpath, code=code_dir_subpath
    )
    mlflow_model.save(os.path.join(path, MLMODEL_FILE_NAME))

    if conda_env is None:
        if pip_requirements is None:
            default_reqs = get_default_pip_requirements()
            # To ensure `_load_pyfunc` can successfully load the model during the dependency
            # inference, `mlflow_model.save` must be called beforehand to save an MLmodel file.
            inferred_reqs = mlflow.models.infer_pip_requirements(
                path,
                FLAVOR_NAME,
                fallback=default_reqs,
            )
            default_reqs = sorted(set(inferred_reqs).union(default_reqs))
        else:
            default_reqs = None
        conda_env, pip_requirements, pip_constraints = _process_pip_requirements(
            default_reqs,
            pip_requirements,
            extra_pip_requirements,
        )
    else:
        conda_env, pip_requirements, pip_constraints = _process_conda_env(conda_env)

    with open(os.path.join(path, _CONDA_ENV_FILE_NAME), "w") as f:
        yaml.safe_dump(conda_env, stream=f, default_flow_style=False)

    # Save `constraints.txt` if necessary
    if pip_constraints:
        write_to(os.path.join(path, _CONSTRAINTS_FILE_NAME), "\n".join(pip_constraints))

    # Save `requirements.txt`
    write_to(os.path.join(path, _REQUIREMENTS_FILE_NAME), "\n".join(pip_requirements))

    _PythonEnv.current().to_yaml(os.path.join(path, _PYTHON_ENV_FILE_NAME))




#@format_docstring(LOG_MODEL_PARAM_DOCS.format(package_name=FLAVOR_NAME))
"""def log_model(h2o3_artifact_location,
              artifact_path,
              h2o3_model_download_location="/tmp/" + str(uuid.uuid1()),
              conda_env=None,
              registered_model_name=None,
              signature: ModelSignature = None,
              input_example: ModelInputExample = None,
              pip_requirements=None,
              extra_pip_requirements=None,
              **kwargs,
              ):
    model_type = _validate_h2o3_model(h2o3_artifact_location)

    h2o3_model_directory = _create_model_file(h2o3_artifact_location, h2o3_model_download_location)

    return Model.log(
        artifact_path=artifact_path,
        flavor=h2o_mlflow_flavors.h2o3,
        registered_model_name=registered_model_name,
        h2o3_artifact_location=h2o3_artifact_location,
        conda_env=conda_env,
        signature=signature,
        input_example=input_example,
        pip_requirements=pip_requirements,
        extra_pip_requirements=extra_pip_requirements,
        model_type=model_type,
        h2o3_model_directory=h2o3_model_directory,
        **kwargs,
    )

def _validate_h2o3_model(h2o3_model):
    if match_file_from_name_pattern(h2o3_model, H2O3_MODEL_INI):
        return MLFLOW_H2O3_MOJO_ARTIFACT
    else:
        raise MlflowException.invalid_parameter_value("The model is not a valid H2O3 MOJO File")


def _create_model_file(h2o3_model, h2o_dai_model_download_location):
    location = h2o_dai_model_download_location + "/"
    model_file_location = location + "model"
    unzip_specific_file(h2o3_model, H2O3_MODEL_INI, directory=model_file_location)
    dst = shutil.copy(h2o3_model, model_file_location)
    os.rename(dst, model_file_location+"/"+MLFLOW_H2O3_MODEL_FILENAME)
    return model_file_location"""

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
    :param kwargs: kwargs to pass to ``h2o.save_model`` method.
    :return: A :py:class:`ModelInfo <mlflow.models.model.ModelInfo>` instance that contains the
             metadata of the logged model.
    """
    return Model.log(
        artifact_path=artifact_path,
        flavor=mlflow.h2o,
        registered_model_name=registered_model_name,
        h2o_model=h2o_model,
        conda_env=conda_env,
        code_paths=code_paths,
        signature=signature,
        input_example=input_example,
        pip_requirements=pip_requirements,
        extra_pip_requirements=extra_pip_requirements,
        metadata=metadata,
        **kwargs,
    )



def _load_model(path, init=False):
    import h2o

    path = os.path.abspath(path)
    with open(os.path.join(path, "h2o.yaml")) as f:
        params = yaml.safe_load(f.read())
    if init:
        h2o.init(**(params["init"] if "init" in params else {}))
        h2o.no_progress()

    model_path = os.path.join(path, params["model_file"])
    if hasattr(h2o, "upload_model"):
        model = h2o.upload_model(model_path)
    else:
        warnings.warn(
            "If your cluster is remote, H2O may not load the model correctly. "
            "Please upgrade H2O version to a newer version"
        )
        model = h2o.load_model(model_path)

    return model


class _H2OModelWrapper:
    def __init__(self, h2o_model):
        self.h2o_model = h2o_model

    def predict(
        self, dataframe, params: Optional[Dict[str, Any]] = None
    ):  # pylint: disable=unused-argument
        """
        :param dataframe: Model input data.
        :param params: Additional parameters to pass to the model for inference.

                       .. Note:: Experimental: This parameter may change or be removed in a future
                                               release without warning.

        :return: Model predictions.
        """
        import h2o

        predicted = self.h2o_model.predict(h2o.H2OFrame(dataframe)).as_data_frame()
        predicted.index = dataframe.index
        return predicted


def _load_pyfunc(path):
    """
    Load PyFunc implementation. Called by ``pyfunc.load_model``.

    :param path: Local filesystem path to the MLflow Model with the ``h2o`` flavor.
    """
    return _H2OModelWrapper(_load_model(path, init=True))


def load_mojo_model(mojo_path, model_id=None,estimator=None):
    """
    Uploads an existing MOJO model from local filesystem into H2O and imports it as an H2O Generic Model. 

    :param mojo_path:  Path to the MOJO archive on the user's local filesystem
    :param model_id: Model ID, default None
    :param estimator: uses H2OGenericEstimator on default None. ,estimator=None
    :return: An H2OGenericEstimator instance embedding given MOJO
    """
    hc = H2OContext.getOrCreate()
    if mojo_path is None:
        raise TypeError("MOJO path may not be None")
    if estimator != None:
        mojo_estimator = estimator(mojo_path,model_id)
    else:
        mojo_estimator = H2OGenericEstimator.from_file(mojo_path, model_id)
    return mojo_estimator

"""    if mojo_path is None:
        raise TypeError("MOJO path may not be None")
    hc = H2OContext.getOrCreate()
    #original_model_filename = model.download_mojo(model_uri)
    return h2o.import_mojo(mojo_path)"""
"""    if estimator is None:
        return h2o.import_mojo(mojo_path, dst_path=None)
    else:
        response = api("POST /3/PostFile", filename=mojo_path)
        frame_key = response["destination_frame"]"""
