# -*- encoding: utf-8 -*-
import h2o_mlops_client

TEMPORARY_DIRECTORY_FOR_MOJO_MODELS="/tmp"

class QueryUtils:
    
    @staticmethod
    def filter_by(field_name, value):
        filter = h2o_mlops_client.StorageFilterRequest(
            query=h2o_mlops_client.StorageQuery(
                clause=[
                    h2o_mlops_client.StorageClause(
                        property_constraint=[
                            h2o_mlops_client.StoragePropertyConstraint(
                                _property=h2o_mlops_client.StorageProperty(
                                    field=field_name
                                ),
                                operator=h2o_mlops_client.StorageOperator.EQUAL_TO,
                                value=h2o_mlops_client.StorageValue(
                                    string_value=value
                                ),
                            )
                        ]
                    )
                ]
            )
        )
        return filter
