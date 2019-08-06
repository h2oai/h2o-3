package hex.genmodel.attributes;


import java.io.Serializable;

/**
 * Counterpart for ColSpecifierV3 in h2o-genmodele module
 */
public class VecSpecifier implements Serializable {

    public String _column_name;
    public String[] _is_member_of_frames;
}
