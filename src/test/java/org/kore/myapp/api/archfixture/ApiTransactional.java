package org.kore.myapp.api.archfixture;

import org.springframework.transaction.annotation.Transactional;

/** Architecture-rule fixture (AR-06): transactions belong to application services. Never used at runtime. */
@Transactional
public class ApiTransactional {
    @jakarta.transaction.Transactional
    void jakartaTransactional() {
    }
}
