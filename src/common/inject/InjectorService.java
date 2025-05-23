package common.inject;

import common.inject.annotations.Inject;
import common.inject.exceptions.InjectException;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class InjectorService {
    private static InjectorService instance;
    private final Map<Class<?>, Class<?>> classMap;
    private final Map<Class<?>, Object> instanceMap;
    private final ThreadLocal<Set<Class<?>>> resolutionStack = ThreadLocal.withInitial(HashSet::new);

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

    public <T> void register(Class<T> abstraction, Class<? extends T> implementation) {
        if (classMap.containsKey(abstraction)) {
            throw new InjectException("Class " + abstraction.getName() + " is already registered.");
        }
        classMap.put(abstraction, implementation);
    }

    public <T> void register(Class<T> clazz) {
        if (classMap.containsKey(clazz)) {
            throw new InjectException("Class " + clazz.getName() + " is already registered.");
        }
        classMap.put(clazz, clazz);
    }

    public <T> T getInstance(Class<T> abstraction) {
        if (instanceMap.containsKey(abstraction)) {
            return abstraction.cast(instanceMap.get(abstraction));
        }

        if (!classMap.containsKey(abstraction)) {
            throw new InjectException("Class " + abstraction.getName() + " is not registered.");
        }

        final Class<?> clazz = classMap.get(abstraction);

        if (!resolutionStack.get().add(clazz)) {
            throw new InjectException("Circular dependency detected for " + abstraction.getName());
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
