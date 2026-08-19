package rpg.core.module;

/**
 * Thrown by {@link ModuleRegistry#getService(Class)} when no module provides the requested service
 * interface.
 *
 * <p>The registry never returns {@code null} for a mandatory lookup; callers that treat a service as
 * optional use {@link ModuleRegistry#findService(Class)} instead.
 */
public class ServiceNotRegisteredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Class<?> serviceInterface;

    public ServiceNotRegisteredException(Class<?> serviceInterface) {
        super("no module registered a service for " + serviceInterface.getName());
        this.serviceInterface = serviceInterface;
    }

    /** The interface that could not be resolved. */
    public Class<?> serviceInterface() {
        return serviceInterface;
    }
}
