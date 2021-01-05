package ai.h2o.targetencoding;

import water.Iced;

public class ColumnsMapping extends Iced {
    private String[] _from;
    private String[] _to;

    public ColumnsMapping(String[] from, String to) {
        this(from, new String[]{to});
    }
    
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
    
    public String toSingle() {
        assert _to.length == 1;
        return _to[0];
    }
}
