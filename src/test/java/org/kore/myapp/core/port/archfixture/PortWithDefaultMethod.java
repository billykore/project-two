package org.kore.myapp.core.port.archfixture;

/** Architecture-rule fixture (AR-05): ports hold declarations only. Never used at runtime. */
public interface PortWithDefaultMethod {
    static String helper() {
        return "static";
    }

    default String withLogic() {
        return "default";
    }
}
