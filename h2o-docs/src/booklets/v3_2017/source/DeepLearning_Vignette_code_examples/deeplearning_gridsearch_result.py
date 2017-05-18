# print model grid search results
model_grid

for model in model_grid:
    print model.model_id + " mse: " + str(model.mse())