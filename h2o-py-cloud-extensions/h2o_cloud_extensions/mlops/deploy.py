# -*- encoding: utf-8 -*-
import h2o_cloud_extensions
import h2o_mlops_client
import time
from .connect import *
from .publish import PUBLISHED_MODEL_ATTRIBUTE_NAME, PUBLISHED_ARTIFACT_ATTRIBUTE_NAME

DEPLOYED_MODEL_DICT_ATTRIBUTE_NAME="_h2o_cloud_extensions_deployed_model_dict"

def deploy(self, environment = None):
    if environment is None:
        environment = h2o_cloud_extensions.settings.mlops.deployment_environment
    elif isinstance(environment, str):
        environment = [environment,]
    assert isinstance(environment, list)
    published_model = getattr(self, PUBLISHED_MODEL_ATTRIBUTE_NAME, None)
    if published_model is None:
        warnings.warn("No model has been published yet!")
        return
    deployed_model = getattr(self, DEPLOYED_MODEL_DICT_ATTRIBUTE_NAME, None)
    if deployed_model is None:
        deployed_model = dict()
        setattr(self,DEPLOYED_MODEL_DICT_ATTRIBUTE_NAME, deployed_model)
    deployed_environment_predicate = \
        lambda e: deployed_model.get(e) is not None and deployed_model.get(e) == published_model
    deployed_environements = [e for e in environment if deployed_environment_predicate(e)]
    undeployed_environements = list([e for e in environment if not deployed_environment_predicate(e)])
    
    for e in deployed_environements:
        warnings.warn("The model '%s' has already been deployed to %s environment." % (published_model, e))
    
    if len(undeployed_environements) > 0:
        mlops_connection = create_mlops_connection()
        project = get_or_create_project(mlops_connection)
        environments = resolve_environments(mlops_connection, project, undeployed_environements)
        artifact = getattr(self, PUBLISHED_ARTIFACT_ATTRIBUTE_NAME, None)
        deployments = \
            [(deploy_artifact(mlops_connection, artifact, project, env, published_model), env) for env in environments]
        for (deployment, env) in deployments:
            max_wait_time_secs = 300 # 5 minutes
            if wait_for_deployment_become_healthy(mlops_connection, deployment, max_wait_time_secs):
                deployed_model[env.display_name] = published_model
                print("Model '%s' was successfully deployed to %s environment." % (published_model, env.display_name))
            else:
                warnings.warn(
                    "Deployment of model '%s' to %s environment failed." \
                    % (published_model, env.display_name))


def resolve_environments(mlops_connection, project, environment_names):
    all_environments = mlops_connection.storage.deployment_environment.list_deployment_environments(
        h2o_mlops_client.StorageListDeploymentEnvironmentsRequest(project.id)
    )
    environments = []    
    for environment_name in environment_names:
        environment = None
        for candidate in all_environments.deployment_environment:
            if candidate.display_name == environment_name:
                environment = candidate
                break
        if environment:
            environments.append(environment)
        else:
            warnings.warn("Environment '%s' does not exist." % environment)
    
    return environments

def deploy_artifact(mlops_connection, artifact, project, environment, model_name):
    composition = h2o_mlops_client.DeployDeploymentComposition(
        experiment_id=artifact.entity_id,
        artifact_id=artifact.id,
        deployable_artifact_type_name="h2o3_mojo",
        artifact_processor_name="h2o3_mojo_extractor",
        runtime_name="h2o3_mojo_runtime"
    )
    to_deploy = h2o_mlops_client.DeployDeployment(
        project_id=project.id,
        display_name = model_name + "_" + environment.display_name, 
        deployment_environment_id=environment.id,
        single_deployment=h2o_mlops_client.DeploySingleDeployment(
            deployment_composition=composition
        )
    )
    deployed_deployment = mlops_connection.deployer.deployment.create_deployment(
        h2o_mlops_client.DeployCreateDeploymentRequest(deployment=to_deploy)
    ).deployment
    return deployed_deployment

def wait_for_deployment_become_healthy(mlops_connection, deployment, max_wait_time_secs):
    svc = mlops_connection.deployer.deployment_status
    status: h2o_mlops_client.DeployDeploymentStatus
    deadline = time.monotonic() + max_wait_time_secs

    while True:
        time.sleep(10)
        status = svc.get_deployment_status(
            h2o_mlops_client.DeployGetDeploymentStatusRequest(deployment_id=deployment.id)
        ).deployment_status
        if (
            status.state == h2o_mlops_client.DeployDeploymentState.HEALTHY
            or time.monotonic() > deadline
        ):
            break
    return status.state == h2o_mlops_client.DeployDeploymentState.HEALTHY
