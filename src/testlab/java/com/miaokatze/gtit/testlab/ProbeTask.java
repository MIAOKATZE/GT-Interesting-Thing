// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtit.testlab;

/** One bounded server-thread step per tick. Return true when finished; never sleep or loop waiting for world ticks. */
public interface ProbeTask extends AutoCloseable {

    boolean tick() throws Exception;

    @Override
    default void close() throws Exception {}
}
