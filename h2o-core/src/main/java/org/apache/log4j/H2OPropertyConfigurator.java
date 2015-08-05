package org.apache.log4j;

import org.apache.log4j.spi.LoggerRepository;

import java.util.Properties;

/**
 * Append to an existing live log4j configuration rather than to create a new one
 * with a new complete properties file.
 *
 * This is used by embedded environments like Sparkling Water that don't want to
 * blindly clobber the parent logger configuration.
 */
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
