package common.inject;

import common.inject.annotations.Inject;
import common.inject.exceptions.InjectException;
import common.logger.Logger;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class InjectorService {
    private static InjectorService instance;
    private static final Logger LOGGER = new Logger(InjectorService.class);
    private static boolean didInit = false;
    private final Map<Class<?>, Class<?>> classMap;
    private final Map<Class<?>, Object> instanceMap;
    private final ThreadLocal<Set<Class<?>>> resolutionStack = ThreadLocal.withInitial(HashSet::new);
    private static String basePackage = "";

    private InjectorService() {
        classMap = new HashMap<>();
        instanceMap = new HashMap<>();
    }

    public static InjectorService getInstance() {
        if (instance == null) {
            instance = new InjectorService();
        }
        return instance;
    }

    public static void setBasePackage(String packageName) {
        basePackage = packageName;
    }

    public void reset() {
        classMap.clear();
        instanceMap.clear();
        resolutionStack.get().clear();
        didInit = false;
        LOGGER.debug("InjectorService reset");
    }

    public <T> void register(Class<T> abstraction, Class<? extends T> implementation) {
        if (classMap.containsKey(abstraction)) {
            throw new InjectException("Class " + abstraction.getName() + " is already registered.");
        }
        LOGGER.debug("Registering " + implementation.getName() + " as abstraction " + abstraction.getName());
        classMap.put(abstraction, implementation);
    }

    public <T> void register(Class<T> clazz) {
        if (classMap.containsKey(clazz)) {
            throw new InjectException("Class " + clazz.getName() + " is already registered.");
        }
        LOGGER.debug("Registering " + clazz.getName() + " as itself");
        classMap.put(clazz, clazz);
    }

    public <T> T getInstanceNullable(Class<T> abstraction) {
        try {
            return getInstance(abstraction);
        } catch (InjectException e) {
            LOGGER.debug("Failed to get instance of " + abstraction.getName() + ", returning null");
            return null;
        }
    }

    public <T> T getNewInstanceNullable(Class<T> abstraction) {
        try {
            return getNewInstance(abstraction);
        } catch (InjectException e) {
            LOGGER.debug("Failed to get new instance of " + abstraction.getName() + ", returning null");
            return null;
        }
    }

    public <T> T getInstance(Class<T> abstraction) {
        if (!didInit) {
            InjectionDiscoverer.discover(basePackage);
            didInit = true;
        }

        if (instanceMap.containsKey(abstraction)) {
            LOGGER.debug("Returning cached instance of " + abstraction.getName());
            return abstraction.cast(instanceMap.get(abstraction));
        }
        return getNewInstance(abstraction);
    }

    public <T> T getNewInstance(Class<T> abstraction) {
        if (!classMap.containsKey(abstraction)) {
            throw new InjectException("Class " + abstraction.getName() + " is not registered.");
        }

        LOGGER.debug("Creating " + abstraction.getName() + " instance");
        final Class<?> clazz = classMap.get(abstraction);

        if (!resolutionStack.get().add(clazz)) {
            throw new InjectException("Circular dependency detected: " + String.join(" -> ", resolutionStack.get().stream().map(Class::getSimpleName).toList()) + " -> " + clazz.getSimpleName());
        }

        try {
            @SuppressWarnings("unchecked") final T instance = (T) createInstance(clazz);
            instanceMap.put(abstraction, instance);
            return instance;
        } finally {
            resolutionStack.get().remove(clazz);
        }
    }

    private <T> T createInstance(Class<T> implementationClass) {
        try {
            final Constructor<?>[] constructors = implementationClass.getConstructors();
            if (constructors.length == 0) {
                throw new InjectException("No public constructor found for: " + implementationClass.getName());
            }

            Constructor<?> constructor = constructors[0];

            for (Constructor<?> c : constructors) {
                if (c.isAnnotationPresent(Inject.class)) {
                    constructor = c;
                }
            }

            LOGGER.debug("Using constructor " + constructor + " for " + implementationClass.getName());

            final Class<?>[] paramTypes = constructor.getParameterTypes();

            final Object[] dependencies = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                dependencies[i] = getInstance(paramTypes[i]);
            }

            return implementationClass.cast(constructor.newInstance(dependencies));
        } catch (Exception e) {
            throw new InjectException("Failed to instantiate: " + implementationClass.getName(), e);
        }
    }
}
