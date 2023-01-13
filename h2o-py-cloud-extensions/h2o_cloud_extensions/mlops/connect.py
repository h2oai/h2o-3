# -*- encoding: utf-8 -*-
import h2o_cloud_extensions
import h2o_mlops_client
import warnings
from .utils import *

def create_mlops_connection():
    mlops_token_provider = h2o_mlops_client.TokenProvider(
        refresh_token=h2o_cloud_extensions.settings.connection.refresh_token,
        client_id=h2o_cloud_extensions.settings.connection.client_id,
        token_endpoint_url=h2o_cloud_extensions.settings.connection.token_endpoint_url
    )

    mlops_client = h2o_mlops_client.Client(
        gateway_url=h2o_cloud_extensions.settings.mlops.api_url,
        token_provider=mlops_token_provider
    )
    
    return mlops_client

def get_or_create_project(mlops_connection: h2o_mlops_client.Client):
    project_name = h2o_cloud_extensions.settings.mlops.project_name
    project_description = h2o_cloud_extensions.settings.mlops.project_description
    projects = mlops_connection.storage.project.list_projects(
        h2o_mlops_client.StorageListProjectsRequest(
            filter=QueryUtils.filter_by("display_name", project_name)
        )
    ).project
    if len(projects) > 1:
        warnings.warn("The mlops instance contains more projects with display_name '%s':\n%s" %(project_name, projects))
        warnings.warn("Selecting project with id '%s'" %(projects[0].id,))
    if len(projects) == 0:
        project = mlops_connection.storage.project.create_project({
            'project': {
                'description': project_description,
                'display_name': project_name,
            }
        }).project
    else:
        project = projects[0]
    return project
