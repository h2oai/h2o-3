package water.api.schemas3;

import water.api.ValuesProvider;
import water.parser.ParserService;

/**
 */
public class ParseTypeValuesProvider implements ValuesProvider {

  @Override
  public String[] values() {
    return ParserService.INSTANCE.getAllProviderNames(true);
  }
}
