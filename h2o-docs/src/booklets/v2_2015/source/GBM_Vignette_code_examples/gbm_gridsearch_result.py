# print out all prediction errors and run times of the models
gs

# print out the auc for all of the models
for g in gs:
    print g.model_id + " auc: " + str(g.auc())
