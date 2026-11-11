package org.kore.myapp.adapter.fixtureone;

import org.kore.myapp.adapter.fixturetwo.AdapterTwo;

/** Architecture-rule fixture (AR-02): adapters must not call each other. Never used at runtime. */
public class AdapterOne {
    AdapterTwo other;
}
