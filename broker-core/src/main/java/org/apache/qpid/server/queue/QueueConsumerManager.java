/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.queue;

import java.util.Iterator;

public interface QueueConsumerManager
{
    void addConsumer(QueueConsumer<?> consumer);
    boolean removeConsumer(QueueConsumer<?> consumer);
    /*public*/ boolean setInterest(QueueConsumer<?> consumer, boolean interested); // called from Consumer
    /*private*/ boolean setNotified(QueueConsumer<?> consumer, boolean notified); // called from Queue

    // should be priority and then insertion order
    Iterator<QueueConsumer<?>> getInterestedIterator();

    Iterator<QueueConsumer<?>> getAllIterator();
    Iterator<QueueConsumer<?>> getNonAcquiringIterator();

    Iterator<QueueConsumer<?>> getPrioritySortedNotifiedOrInterestedIterator();

    int getAllSize();
    //        int getInterestedSize();
    int getNotifiedAcquiringSize();


}
