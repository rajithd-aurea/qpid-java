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
package org.apache.qpid.server.virtualhostnode;

import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.virtualhost.AbstractNonConnectionAcceptingVirtualHost;

@ManagedObject( category = false, type = RedirectingVirtualHostImpl.VIRTUAL_HOST_TYPE, register = false,
                description = VirtualHost.CLASS_DESCRIPTION)
class RedirectingVirtualHostImpl
        extends AbstractNonConnectionAcceptingVirtualHost<RedirectingVirtualHostImpl>
        implements RedirectingVirtualHost<RedirectingVirtualHostImpl>
{
    public static final String VIRTUAL_HOST_TYPE = "REDIRECTOR";

    @ManagedObjectFactoryConstructor
    public RedirectingVirtualHostImpl(final Map<String, Object> attributes, VirtualHostNode<?> virtualHostNode)
    {
        super(parentsMap(virtualHostNode), attributes);
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);

        if (changedAttributes.contains(DESIRED_STATE) && proxyForValidation.getDesiredState() == State.DELETED)
        {
            throw new IllegalConfigurationException("Directly deleting a redirecting virtualhost is not supported. "
            + "Delete the parent virtual host node '" + getParent(VirtualHostNode.class) + "' instead.");
        }
        else
        {
            throw new IllegalConfigurationException("A redirecting virtualhost does not support changing of"
                                                    + " its attributes");
        }
    }


    @Override
    public String getRedirectHost(final AmqpPort<?> port)
    {
        return ((RedirectingVirtualHostNode<?>)(getParent(VirtualHostNode.class))).getRedirects().get(port);
    }


  }
