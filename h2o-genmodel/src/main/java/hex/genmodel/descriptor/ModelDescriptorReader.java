package hex.genmodel.descriptor;

import hex.genmodel.ModelDescriptor;

/**
 * Implementations of this interface are able to extract additional information from serialized models.
 * The amount of 
 */
public interface ModelDescriptorReader {

    /**
     * Read all the available description of a model defined by {@link ModelDescriptor}
     *
     * @return An instance of {@link ModelDescriptor}, if available. Otherwise null.
     */
    ModelDescriptor getDescription();
}
