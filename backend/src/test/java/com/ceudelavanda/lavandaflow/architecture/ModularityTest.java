package com.ceudelavanda.lavandaflow.architecture;

import com.ceudelavanda.lavandaflow.LavandaFlowApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void verifiesApplicationModuleBoundaries() {
        ApplicationModules.of(LavandaFlowApplication.class).verify();
    }
}
