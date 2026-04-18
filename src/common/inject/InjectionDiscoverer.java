package common.inject;

import common.inject.api.RegisterFor;
import common.logger.Logger;
import common.util.Classloader;

import java.util.List;

public class InjectionDiscoverer extends Classloader<Object> {
    private static final Logger LOGGER = new Logger(InjectionDiscoverer.class);

    private InjectionDiscoverer(String packageName) {
        this.packageName = packageName;
    }

    private InjectionDiscoverer() {
        this("");
    }

    public static void discover(String rootPackage, List<String> packagesBlackList) {
        if (rootPackage.startsWith(".")) {
            rootPackage = rootPackage.substring(1);
        }

        InjectionDiscoverer discoverer = new InjectionDiscoverer(rootPackage);
        discoverer.loadClasses();

        String finalRootPackage = rootPackage;
        discoverer.getPackages().forEach(subpackage -> {
            if (packagesBlackList.contains(finalRootPackage + "." + subpackage) || packagesBlackList.contains(subpackage)) {
                LOGGER.debug("Skipping package " + finalRootPackage + "." + subpackage + " because it is in the blacklist.");
                return;
            }
            InjectionDiscoverer.discover(finalRootPackage + "." + subpackage, packagesBlackList);
        });
    }

    public static void discover() {
        discover("", List.of());
    }

    public static void discover(String rootPackage) {
        discover(rootPackage, List.of());
    }

    public static void discover(List<String> packagesBlackList) {
        discover("", packagesBlackList);
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
