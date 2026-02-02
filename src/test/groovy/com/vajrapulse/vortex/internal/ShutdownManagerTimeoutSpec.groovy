package com.vajrapulse.vortex.internal

import com.vajrapulse.vortex.Backend
import com.vajrapulse.vortex.results.BatchResult
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ShutdownManagerTimeoutSpec extends Specification {

    def "dispatchWithTimeout throws TimeoutException when backend blocks past timeout"() {
        given:
        def latch = new CountDownLatch(1)
        Backend<String> backend = { batch ->
            latch.await(5, TimeUnit.SECONDS)
            new BatchResult<>([], [])
        }

        when:
        ShutdownManager.dispatchWithTimeout(backend, ["a", "b"], 20)

        then:
        def ex = thrown(RuntimeException)
        ex.cause instanceof TimeoutException
    }
}

