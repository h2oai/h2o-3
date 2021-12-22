package ai.h2o.targetencoding;

import water.Iced;

public class ColumnsMapping extends Iced {
    private String[] _from;
    private String[] _to;

    public ColumnsMapping(String[] from, String[] to) {
        _from = from;
        _to = to;
    }

    public String[] from() {
        return _from;
    }

    public String[] to() {
        return _to;
    }
}

class ColumnsToSingleMapping extends ColumnsMapping {
    
    private String[] _toDomain;
    
    public ColumnsToSingleMapping(String[] from, String to, String[] toDomain) {
        super(from, new String[]{to});
        _toDomain = toDomain;
    }
    
    public String toSingle() {
        return to()[0];
    }
    
    public String[] toDomain() {
        return _toDomain;
    }
}
