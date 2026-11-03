package com.webtoapp.core.nodejs

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class NodeLinkerPathTest {

    private val sep = File.pathSeparator

    @Test
    fun `libnode directory is searched before the app lib dir`() {
        assertThat(nodeLinkerPath("/data/node/arm64-v8a", "/data/app/lib", ""))
            .isEqualTo("/data/node/arm64-v8a" + sep + "/data/app/lib")
    }

    @Test
    fun `duplicate directories are kept once`() {
        val existing = "/data/app/lib" + sep + "/opt"
        assertThat(nodeLinkerPath("/data/app/lib", "/data/app/lib", existing))
            .isEqualTo("/data/app/lib" + sep + "/opt")
    }
}
