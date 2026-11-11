package org.kore.myapp.core.archfixture;

import org.springframework.stereotype.Component;

/** Architecture-rule fixture (AR-03, AR-08): core must not use Spring. Never used at runtime. */
@Component
public class CoreUsesSpring {
}
