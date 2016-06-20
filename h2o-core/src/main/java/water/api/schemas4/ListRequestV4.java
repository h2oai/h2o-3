package water.api.schemas4;

import water.Iced;

/**
 * Common input schema class for endpoints that request collections of objects. For example,
 *   GET /4/schemas
 *   GET /4/frames
 *   GET /4/models
 *   etc.
 * This class is a placeholder right now, but eventually it can host functionality such as filtering/sorting the
 * results, providing cursor capabilities, etc.
 *   TODO add cursor fields {limit} and {offset}
 */
public class ListRequestV4 extends OutputSchemaV4<Iced, ListRequestV4> {

}
