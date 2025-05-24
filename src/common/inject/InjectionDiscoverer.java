package common.inject;

import common.inject.annotations.RegisterFor;
import common.logger.Logger;
import common.util.Classloader;

public class InjectionDiscoverer extends Classloader<Object> {
    private static final Logger LOGGER = new Logger(InjectionDiscoverer.class);

    private InjectionDiscoverer(String packageName) {
        this.packageName = packageName;
    }

    private InjectionDiscoverer() {
        this("");
    }

    public static void discover(String rootPackage) {
        if (rootPackage.startsWith(".")) {
            rootPackage = rootPackage.substring(1);
        }

        InjectionDiscoverer discoverer = new InjectionDiscoverer(rootPackage);
        discoverer.loadClasses();

        String finalRootPackage = rootPackage;
        discoverer.getPackages().forEach(subpackage -> InjectionDiscoverer.discover(finalRootPackage + "." + subpackage));
    }

    public static void discover() {
        discover("");
    }

    @Override
    protected Class<?> filterClasses(String s) {
        final Class<?> clazz = defaultClassFilter(s);

        if (clazz == null) {
            return null;
        }

        if (!clazz.isAnnotationPresent(RegisterFor.class)) {
            return clazz;
        }

        final Class<?> abstraction = clazz.getAnnotation(RegisterFor.class).value();

        if (abstraction == null) {
            LOGGER.warn("Class " + clazz.getName() + " is annotated with @RegisterFor but has no value specified. It will not be registered.");
            return null;
        }

        if (!abstraction.isAssignableFrom(clazz)) {
            LOGGER.warn("Class " + clazz.getName() + " is annotated with @RegisterFor but does not implement the specified interface " + abstraction.getName() + ". It will not be registered.");
            return null;
        }

        final InjectorService service = InjectorService.getInstance();

        @SuppressWarnings({"unchecked", "rawtypes"})
        Class rawAbstraction = (Class) abstraction;

        service.register(rawAbstraction, clazz);

        LOGGER.debug("Discovered and registered class: " + clazz.getName());

        return clazz;
    }
}
