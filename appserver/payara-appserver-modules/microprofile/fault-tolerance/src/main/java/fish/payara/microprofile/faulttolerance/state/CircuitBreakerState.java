/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *    Copyright (c) [2017-2018] Payara Foundation and/or its affiliates. All rights reserved.
 *
 *     The contents of this file are subject to the terms of either the GNU
 *     General Public License Version 2 only ("GPL") or the Common Development
 *     and Distribution License("CDDL") (collectively, the "License").  You
 *     may not use this file except in compliance with the License.  You can
 *     obtain a copy of the License at
 *     https://github.com/payara/Payara/blob/master/LICENSE.txt
 *     See the License for the specific
 *     language governing permissions and limitations under the License.
 *
 *     When distributing the software, include this License Header Notice in each
 *     file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *     GPL Classpath Exception:
 *     The Payara Foundation designates this particular file as subject to the "Classpath"
 *     exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *     file that accompanied this code.
 *
 *     Modifications:
 *     If applicable, add the following below the License Header, with the fields
 *     enclosed by brackets [] replaced by your own identifying information:
 *     "Portions Copyright [year] [name of copyright owner]"
 *
 *     Contributor(s):
 *     If you wish your version of this file to be governed by only the CDDL or
 *     only the GPL Version 2, indicate your decision by adding "[Contributor]
 *     elects to include this software in this distribution under the [CDDL or GPL
 *     Version 2] license."  If you don't indicate a single choice of license, a
 *     recipient has the option to distribute your version of this file under
 *     either the CDDL, the GPL Version 2 or to extend the choice of license to
 *     its licensees as provided above.  However, if you add GPL Version 2 code
 *     and therefore, elected the GPL Version 2 license, then the option applies
 *     only if the new code is made subject to such option by the copyright
 *     holder.
 */
package fish.payara.microprofile.faulttolerance.state;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that represents the state of a CircuitBreaker.
 * @author Andrew Pielage
 */
public class CircuitBreakerState {

    private static final Logger LOGGER = Logger.getLogger(CircuitBreakerState.class.getName());

    public enum CircuitState {
        OPEN, CLOSED, HALF_OPEN
    }

    private final BlockingQueue<Boolean> closedResultsQueue;

    private volatile CircuitState circuitState = CircuitState.CLOSED;
    private volatile ZonedDateTime stateSince = ZonedDateTime.now();

    private final AtomicInteger halfOpenSuccessfulResultsCounter = new AtomicInteger(0);
    private final AtomicLong closedTime = new AtomicLong(0);
    private final AtomicLong openTime = new AtomicLong(0);
    private final AtomicLong halfOpenTime = new AtomicLong(0);

    public CircuitBreakerState(int requestVolumeThreshold) {
        this.closedResultsQueue = new LinkedBlockingQueue<>(requestVolumeThreshold);
    }

    /**
     * Gets the current circuit state.
     * @return The current circuit state
     */
    public CircuitState getCircuitState() {
        return this.circuitState;
    }

    /**
     * Sets the CircuitBreaker state to the provided enum value.
     * @param circuitState The state to set the CircuitBreaker to.
     */
    public void setCircuitState(CircuitState circuitState) {
        switch (this.circuitState) {
            case OPEN:
                updateOpenTime();
                break;
            case HALF_OPEN:
                updateHalfOpenTime();
                break;
            case CLOSED:
                updateClosedTime();
                break;
        }
        this.circuitState = circuitState;
    }

    /**
     * Records a success or failure result to the CircuitBreaker.
     * @param result True for a success, false for a failure
     */
    public void recordClosedResult(Boolean result) {
        // If the queue is full, remove the oldest result and add
        if (!this.closedResultsQueue.offer(result)) {
            this.closedResultsQueue.poll();
            this.closedResultsQueue.offer(result);
        }
    }

    /**
     * Clears the results queue.
     */
    public void resetResults() {
        this.closedResultsQueue.clear();
    }

    /**
     * Increments the successful results counter for the half open state.
     */
    public void incrementHalfOpenSuccessfulResultCounter() {
        this.halfOpenSuccessfulResultsCounter.incrementAndGet();
    }

    /**
     * Resets the successful results counter for the half open state.
     */
    public void resetHalfOpenSuccessfulResultCounter() {
        this.halfOpenSuccessfulResultsCounter.set(0);
    }

    /**
     * Gets the successful results counter for the half open state.
     * @return The number of consecutive successful results.
     */
    public int getHalfOpenSuccessFulResultCounter() {
        return this.halfOpenSuccessfulResultsCounter.get();
    }

    /**
     * Checks to see if the CircuitBreaker is over the given failure threshold.
     * @param failureThreshold The number of failures before the circuit breaker should open
     * @return True if the CircuitBreaker is over the failure threshold
     */
    public boolean isOverFailureThreshold(long failureThreshold) {
        boolean over = false;
        int failures = 0;

        // Only check if the queue is full
        if (this.closedResultsQueue.remainingCapacity() == 0) {
            for (Boolean success : this.closedResultsQueue) {
                if (!success) {
                    failures++;

                    if (failures == failureThreshold) {
                        over = true;
                        break;
                    }
                }
            }
        } else {
            LOGGER.log(Level.FINE, "CircuitBreaker results queue isn't full yet.");
        }

        return over;
    }

    public long updateAndGetClosedTime() {
        return CircuitState.CLOSED.equals(this.circuitState)
            ? updateClosedTime()
            : this.closedTime.get();
    }

    private long updateClosedTime() {
        ZonedDateTime now = ZonedDateTime.now();
        long nanos = this.closedTime.addAndGet(Duration.between(this.stateSince, now).toNanos());
        this.stateSince = now;
        return nanos;
    }

    public long updateAndGetOpenTime() {
        return CircuitState.OPEN.equals(this.circuitState)
            ? updateOpenTime()
            : this.openTime.get();
    }

    private long updateOpenTime() {
        ZonedDateTime now = ZonedDateTime.now();
        long nanos = this.openTime.addAndGet(Duration.between(this.stateSince, now).toNanos());
        this.stateSince = now;
        return nanos;
    }

    public long updateAndGetHalfOpenTime() {
        return CircuitState.HALF_OPEN.equals(this.circuitState)
            ? updateHalfOpenTime()
            : this.halfOpenTime.get();
    }

    private long updateHalfOpenTime() {
        ZonedDateTime now = ZonedDateTime.now();
        long nanos = this.halfOpenTime.addAndGet(Duration.between(this.stateSince, now).toNanos());
        this.stateSince = now;
        return nanos;
    }
}
