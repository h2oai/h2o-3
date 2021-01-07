package hex.genmodel.algos.targetencoder;

public class ColumnsMapping {
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
