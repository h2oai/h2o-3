makeXY <- function(base, num.common.cols, all.match, duplicates.in.x, duplicates.in.y) {
    int_set          = -10000:10000
    str_set          = combn(LETTERS, 5, paste, collapse = "")
    x_shared_cols    = NULL
    y_shared_cols    = NULL
    rows             = nrow(base)

    # generate num.common.cols columns names
    shared_column_names = sapply(1:num.common.cols, function (c) paste0("shared",c))

    # generate num.common.cols int or str columns
    for(n in shared_column_names) {
        int_col = FALSE
        if (sample(0:1,1)) { # int
            x_shared_cols[[n]] = sample(int_set, rows)
            int_col = TRUE
        } else { # str
            x_shared_cols[[n]] = as.factor(sample(str_set, rows))
        }
        if (duplicates.in.x) {
            num_dups = sample(2:500,1)
            x_shared_cols[[n]][(rows-num_dups+1):rows] =  x_shared_cols[[n]][1:num_dups]
        }
        if (all.match) {
            if (xor(duplicates.in.x, duplicates.in.y)) { stop("Do not support dups in x xor y, with all.match TRUE") }
            y_shared_cols = x_shared_cols
        } else {
            # no matches by default
            if (int_col) {
                new_int_set = int_set[-which(int_set %in% x_shared_cols[[n]])]
                y_shared_cols[[n]] = sample(new_int_set, rows)
            } else {
                new_str_set = str_set[-which(str_set %in% x_shared_cols[[n]])]
                y_shared_cols[[n]] = as.factor(sample(new_str_set, rows))
            }
            if (sample(0:1,1)) { # some matches
                num_matches = sample(2:500,1)
                x_shared_cols[[n]][501:(500+num_matches)] = y_shared_cols[[n]][1:num_matches]
            }
            if (duplicates.in.y) {
                num_dups = sample(2:500,1)
                y_shared_cols[[n]][(rows-num_dups+1):rows] = y_shared_cols[[n]][501:(500+num_dups)]
            }
        }
    }

    x_shared_cols = as.data.frame(x_shared_cols)
    y_shared_cols = as.data.frame(y_shared_cols)
    x = h2o.cbind(as.h2o(x_shared_cols),base)
    y = h2o.cbind(as.h2o(y_shared_cols),base)
    print("X:"); print(x)
    print("Y:"); print(y)
    list(x, y)
}

checkMerge <- function(x, y, by.x, by.y, all.x, all.y) {
    #h2o_merge_result = h2o.merge(x=x, y=y, by.x=by.x, by.y=by.y, all.x=all.x, all.y=all.y, method="radix")
    #h2o_merge_result = h2o.merge(x=x, y=y, by.x=by.x, by.y=by.y, all.x=all.x, all.y=all.y, method="hash")
    base_merge_result = merge(x=as.data.frame(x), y=as.data.frame(y), by.x=by.x, by.y=by.y, all.x=all.x, all.y=all.y)

}

test.merge <- function() {
    # generate "base" dataset
    dataset_params = list()
    dataset_params$rows = sample(5000:10000,1)
    dataset_params$cols = sample(10:20,1)
    dataset_params$categorical_fraction = round(runif(1),1)
    left_over = (1 - dataset_params$categorical_fraction)
    dataset_params$integer_fraction = round(left_over - round(runif(1,min=0,max=left_over),1),1)
    if (dataset_params$integer_fraction + dataset_params$categorical_fraction == 1) {
        if (dataset_params$integer_fraction > dataset_params$categorical_fraction) {
            dataset_params$integer_fraction = dataset_params$integer_fraction - 0.1
        } else {
            dataset_params$categorical_fraction = dataset_params$categorical_fraction - 0.1
        }
    }
    #dataset_params$missing_fraction = runif(1,min=0.0,max=0.5)
    dataset_params$randomize = TRUE
    dataset_params$factors = sample(2:2000,1)
    print("Dataset parameters:"); print(dataset_params)
    fr = do.call(h2o.createFrame, dataset_params)
    print("nrow: "); print(nrow(fr))

    ### 1 col, all.x=F, all.y=F, all match, no duplicates
    ### 1 col, all.x=F, all.y=F, all match, duplicates in x, y

    ### 1 col, all.x=F, all.y=F, x non-matches, no duplicates
    ### 1 col, all.x=F, all.y=F, x non-matches, duplicates in x
    ### 1 col, all.x=F, all.y=F, x non-matches, duplicates in y
    ### 1 col, all.x=F, all.y=F, x non-matches, duplicates in x, y

    ### 1 col, all.x=F, all.y=F, y non-matches, no duplicates,
    ### 1 col, all.x=F, all.y=F, y non-matches, duplicates in x
    ### 1 col, all.x=F, all.y=F, y non-matches, duplicates in y
    ### 1 col, all.x=F, all.y=F, y non-matches, duplicates in x, y

    ### 1 col, all.x=F, all.y=F, x,y non-matches, no duplicates,
    ### 1 col, all.x=F, all.y=F, x,y non-matches, duplicates in x
    ### 1 col, all.x=F, all.y=F, x,y non-matches, duplicates in y
    ### 1 col, all.x=F, all.y=F, x,y non-matches, duplicates in x, y
    for (nc in 1:3) {
        for (am in c(TRUE, FALSE)) {
            for (ax in c(TRUE, FALSE)) {
                for (ay in c(TRUE, FALSE)) {
                    dxdy = NULL
                    if (am) { dxdy = list(dxdy1=c(TRUE,TRUE),dxdy2=c(FALSE,FALSE))
                    } else  { dxdy = list(dxdy1=c(TRUE,TRUE),dxdy2=c(FALSE,FALSE),dxdy3=c(TRUE,FALSE),dxdy2=c(FALSE,TRUE)) }
                    for (d in dxdy) {
                        print(""); print(paste0("########### num.common.cols=", nc,
                                                             ", all.x=", ax,
                                                             ", all.y=", ay,
                                                             ", all.match=", am,
                                                             ", duplicates.in.x=", d[1],
                                                             ", duplicates.in.y=", d[2],
                                                             " ###########")); print("")
                        xy = makeXY(base=fr, num.common.cols=nc, all.match=am, duplicates.in.x=d[1], duplicates.in.y=d[2])
                        checkMerge(x=xy[[1]], y=xy[[2]], by.x=1:nc, by.y=1:nc, all.x=ax, all.y=ay)
                    }
                }
            }
        }
    }
}

doTest("Test merge", test.merge)
