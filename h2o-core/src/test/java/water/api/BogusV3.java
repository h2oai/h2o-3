package water.api;

import org.junit.Ignore;

import hex.schemas.ModelBuilderSchema;
import water.api.schemas3.ModelParametersSchemaV3;

// Need this class, so a /3/Jobs can return the JSON'd version of it
@Ignore("Support for tests, but no actual tests here")
public class BogusV3 extends ModelBuilderSchema<Bogus,BogusV3,ModelParametersSchemaV3> {
}
