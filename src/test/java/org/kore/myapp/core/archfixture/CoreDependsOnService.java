package org.kore.myapp.core.archfixture;

import org.kore.myapp.service.HelloService;

/** Architecture-rule fixture (AR-01): core must not depend on an application service. Never used at runtime. */
public class CoreDependsOnService {
    HelloService service;
}
