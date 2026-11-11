package org.kore.myapp.adapter.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256TokenHasherTest {

    @Test
    void hashesWithSha256AsLowercaseHex() {
        assertThat(new Sha256TokenHasher().hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
