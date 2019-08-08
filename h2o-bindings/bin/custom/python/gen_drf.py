def update_param(name, param, values):
    if name == 'distribution':
        values = [v for v in values if v not in ['custom', 'ordinal', 'quasibinomial']]
    elif name == 'stopping_metric':
        values.remove('anomaly_score')
    return param, values


deprecated = ['offset_column', 'distribution']
