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
package org.apache.qpid.server.logging.messages;

import java.util.List;

import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.util.BrokerTestHelper;

/**
 * Test EXH Log Messages
 */
public class ExchangeMessagesTest extends AbstractTestMessages
{
    public void testExchangeCreated_Transient() throws Exception
    {
        Exchange<?> exchange = BrokerTestHelper.createExchange("test", false, getEventLogger());

        String type = exchange.getType();
        String name = exchange.getName();

        _logMessage = ExchangeMessages.CREATED(type, name, false);
        List<Object> log = getLog();

        String[] expected = {"Create :", "Type:", type, "Name:", name};

        validateLogMessageNoSubject(log, "EXH-1001", expected);
    }

    public void testExchangeCreated_Persistent() throws Exception
    {
        Exchange<?> exchange = BrokerTestHelper.createExchange("test", true, getEventLogger());

        String type = exchange.getType();
        String name = exchange.getName();

        _logMessage = ExchangeMessages.CREATED(type, name, true);
        List<Object> log = getLog();

        String[] expected = {"Create :", "Durable", "Type:", type, "Name:", name};

        validateLogMessageNoSubject(log, "EXH-1001", expected);
    }

    public void testExchangeDeleted()
    {
        _logMessage = ExchangeMessages.DELETED();
        List<Object> log = performLog();

        String[] expected = {"Deleted"};

        validateLogMessage(log, "EXH-1002", expected);
    }

    public void testExchangeDiscardedMessage() throws Exception
    {
        Exchange<?> exchange = BrokerTestHelper.createExchange("test", false, getEventLogger());

        final String name = exchange.getName();
        final String routingKey = "routingKey";
        clearLog();
        _logMessage = ExchangeMessages.DISCARDMSG(name, routingKey);
        List<Object> log = performLog();

        String[] expected = {"Discarded Message :","Name:", "\"" + name + "\"", "Routing Key:", "\"" + routingKey + "\""};

        validateLogMessage(log, "EXH-1003", expected);
    }
}
