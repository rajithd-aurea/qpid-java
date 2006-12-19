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
package org.apache.qpid.client.handler;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQChannelClosedException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInvalidSelectorException;
import org.apache.qpid.client.AMQNoConsumersException;
import org.apache.qpid.client.AMQNoRouteException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.client.protocol.AMQMethodEvent;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ChannelCloseOkBody;

public class ChannelCloseMethodHandler implements StateAwareMethodListener
{
    private static final Logger _logger = Logger.getLogger(ChannelCloseMethodHandler.class);

    private static ChannelCloseMethodHandler _handler = new ChannelCloseMethodHandler();

    public static ChannelCloseMethodHandler getInstance()
    {
        return _handler;
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent evt) throws AMQException
    {
        _logger.debug("ChannelClose method received");
        ChannelCloseBody method = (ChannelCloseBody) evt.getMethod();

        int errorCode = method.replyCode;
        String reason = method.replyText;
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Channel close reply code: " + errorCode + ", reason: " + reason);
        }

        AMQFrame frame = ChannelCloseOkBody.createAMQFrame(evt.getChannelId());
        evt.getProtocolSession().writeFrame(frame);
        if (errorCode != AMQConstant.REPLY_SUCCESS.getCode())
        {
            _logger.error("Channel close received with errorCode " + errorCode + ", and reason " + reason);
            if (errorCode == AMQConstant.NO_CONSUMERS.getCode())
            {
                throw new AMQNoConsumersException("Error: " + reason, null);
            }
            else if (errorCode == AMQConstant.NO_ROUTE.getCode())
            {
                throw new AMQNoRouteException("Error: " + reason, null);
            }
            else if (errorCode == AMQConstant.INVALID_SELECTOR.getCode())
            {
                _logger.info("Broker responded with Invalid Selector.");

                throw new AMQInvalidSelectorException(reason);
            }
            else
            {
                throw new AMQChannelClosedException(errorCode, "Error: " + reason);
            }

        }
        evt.getProtocolSession().channelClosed(evt.getChannelId(), errorCode, reason);
    }
}
