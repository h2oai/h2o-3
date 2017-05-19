r.loco <- function(model, record, replace_val=NULL,regression=TRUE) {

    # Get Features
    features <- model@parameters$x

    # Get Prediction
    if(regression){
        base_prediction <- as.matrix(h2o.predict(model, record)$predict)
    }else{
        base_prediction <- as.matrix(h2o.predict(model, record)$p1)
    }

    # Get Predictions in each feature was replaced with NA
    predictions <- NULL

    for(i in features){
        altered_record <- record
        altered_record[i] <- NA

        if(regression){
            new_prediction <- as.matrix(h2o.predict(model, altered_record)$predict)[, 1]
        }else{
            new_prediction <- as.matrix(h2o.predict(model, altered_record)$p1)[, 1]
        }

        new_prediction <- data.frame('VariableRemoved' = i,
        'BaseProbability' = base_prediction[1,],
        'NewProbability' = new_prediction,
        stringsAsFactors = FALSE)
        predictions <- rbind(predictions, new_prediction)
    }

    rownames(predictions) <- NULL


    # Get Effect of each feature on the prediction
    predictions$EffectOnProbability <- predictions$NewProbability - base_prediction
    loco_predictions <- t(predictions$EffectOnProbability)
    loco_predictions <- cbind(base_prediction[1,],loco_predictions)
    colnames(loco_predictions) = c("base_pred",paste0("rc_",predictions$VariableRemoved))
    rownames(loco_predictions) <- NULL
    loco_predictions <- as.data.frame(loco_predictions)
    return(loco_predictions)
}

r.loco.multinomial <-function(model,record){
    preds = h2o.predict(model,record)

    preds_prob = preds[,2:4]
    preds_prob <- as.matrix(preds_prob)
    # Get Features
    features <- model@parameters$x
    predictions <- NULL
    for(i in features){
        altered_record <- record
        altered_record[i] <- NA
        new_prediction <- as.matrix(as.numeric(h2o.predict(model, altered_record)))
        new_prediction <- new_prediction[,2:4]
        diff = sum(abs(new_prediction-preds_prob[1,]))
        new_prediction <- data.frame('VariableRemoved' = i,
        'diff' = diff,
        stringsAsFactors = FALSE)
        predictions <- rbind(predictions, new_prediction)
    }
    loco_predictions <- t(predictions$diff)
    loco_predictions <- cbind(as.data.frame(preds["predict"]),loco_predictions)
    colnames(loco_predictions) = c("base_pred",paste0("rc_",predictions$VariableRemoved))
    rownames(loco_predictions) <- NULL
    loco_predictions <- as.data.frame(loco_predictions)
    return(loco_predictions)
}

run_loco_r <- function(model, df, regression=TRUE){
    for(i in (1:nrow(df))){
        if(i == 1){
            pred = r.loco(model,df[i, ],regression=regression)
        }else{
            pred = rbind(pred,r.loco(model, df[i, ],regression=regression))
        }

    }
    return(pred)
}

run_loco_r_mult <- function(model, df){
    for(i in (1:nrow(df))){
        if(i == 1){
            pred = r.loco.multinomial(model,df[i, ])
        }else{
            pred = rbind(pred,r.loco.multinomial(model,df[i, ]))
        }

    }
    return(pred)
}