def update_param(name, param):
    if name == 'distribution':
        values = param['values']
        param['values'] = [v for v in values if v not in ['custom', 'ordinal', 'quasibinomial']]
        return param
    elif name == 'stopping_metric':
        param['values'].remove('anomaly_score')
        param['values'].remove('AUUC')
        param['values'].remove('ATE')
        param['values'].remove('ATT')
        param['values'].remove('ATC')
        param['values'].remove('qini')
        return param
    return None  # means no change
