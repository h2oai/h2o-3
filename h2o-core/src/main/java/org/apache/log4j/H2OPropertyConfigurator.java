package org.apache.log4j;

import org.apache.log4j.spi.LoggerRepository;

import java.util.Properties;

public class H2OPropertyConfigurator extends PropertyConfigurator {
  @Override
  public
  void doConfigure(Properties properties, LoggerRepository hierarchy) {
    parseCatsAndRenderers(properties, hierarchy);

    // We don't want to hold references to appenders preventing their
    // garbage collection.
    registry.clear();
  }
}
