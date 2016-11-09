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
package org.apache.qpid.server.management.amqp;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.message.internal.InternalMessage;
import org.apache.qpid.server.model.NamedAddressSpace;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AbstractQueue;
import org.apache.qpid.server.security.SecurityToken;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;

class ManagementNodeConsumer implements ConsumerImpl, MessageDestination
{
    private final long _id = ConsumerImpl.CONSUMER_NUMBER_GENERATOR.getAndIncrement();
    private final ManagementNode _managementNode;
    private final List<ManagementResponse> _queue = Collections.synchronizedList(new ArrayList<ManagementResponse>());
    private final ConsumerTarget _target;
    private final String _name;


    public ManagementNodeConsumer(final String consumerName, final ManagementNode managementNode, ConsumerTarget target)
    {
        _name = consumerName;
        _managementNode = managementNode;
        _target = target;
    }

    @Override
    public void externalStateChange()
    {
        if(!_queue.isEmpty())
        {
            _target.notifyWork();
        }
    }

    @Override
    public AbstractQueue.MessageContainer pullMessage()
    {
        if (!_queue.isEmpty())
        {

            final ManagementResponse managementResponse = _queue.get(0);
            if (!_target.isSuspended() && _target.allocateCredit(managementResponse.getMessage()))
            {
                _queue.remove(0);
                return new AbstractQueue.MessageContainer(managementResponse, null);
            }
        }
        return null;
    }

    @Override
    public long getBytesOut()
    {
        return 0;
    }

    @Override
    public long getMessagesOut()
    {
        return 0;
    }

    @Override
    public long getUnacknowledgedBytes()
    {
        return 0;
    }

    @Override
    public long getUnacknowledgedMessages()
    {
        return 0;
    }

    @Override
    public AMQSessionModel getSessionModel()
    {
        return _target.getSessionModel();
    }

    @Override
    public MessageSource getMessageSource()
    {
        return _managementNode;
    }

    @Override
    public long getConsumerNumber()
    {
        return _id;
    }

    @Override
    public boolean isSuspended()
    {
        return false;
    }

    @Override
    public boolean isClosed()
    {
        return false;
    }

    @Override
    public boolean acquires()
    {
        return true;
    }

    @Override
    public boolean seesRequeues()
    {
        return false;
    }

    @Override
    public void close()
    {
    }


    @Override
    public boolean isActive()
    {
        return false;
    }

    @Override
    public NamedAddressSpace getAddressSpace()
    {
        return _managementNode.getAddressSpace();
    }

    @Override
    public void authorisePublish(final SecurityToken token, final Map<String, Object> arguments)
            throws AccessControlException
    {
        _managementNode.authorisePublish(token, arguments);
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public <M extends ServerMessage<? extends StorableMessageMetaData>> int send(final M message,
                                                                                 final String routingAddress,
                                                                                 final InstanceProperties instanceProperties,
                                                                                 final ServerTransaction txn,
                                                                                 final Action<? super MessageInstance> postEnqueueAction)
    {
        send((InternalMessage)message);
        return 1;
    }

    @Override
    public ConsumerTarget getTarget()
    {
        return _target;
    }

    ManagementNode getManagementNode()
    {
        return _managementNode;
    }

    void send(final InternalMessage response)
    {
        final ManagementResponse responseEntry = new ManagementResponse(this, response);
        _queue.add(responseEntry);
        _target.notifyWork();
    }
}
