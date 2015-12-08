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
package org.apache.qpid.server.configuration.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectAttribute;
import org.apache.qpid.server.model.ConfiguredObjectTypeRegistry;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.BrokerAdapter;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;

public class ManagementModeStoreHandler implements DurableConfigurationStore
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagementModeStoreHandler.class);

    private static final String MANAGEMENT_MODE_PORT_PREFIX = "MANAGEMENT-MODE-PORT-";
    private static final String PORT_TYPE = Port.class.getSimpleName();
    private static final String VIRTUAL_HOST_TYPE = VirtualHost.class.getSimpleName();
    private static final String ATTRIBUTE_STATE = VirtualHost.STATE;
    private static final Object MANAGEMENT_MODE_AUTH_PROVIDER = "mm-auth";


    private final DurableConfigurationStore _store;
    private Map<UUID, ConfiguredObjectRecord> _cliEntries;
    private Map<UUID, Object> _quiescedEntriesOriginalState;
    private final SystemConfig<?> _systemConfig;
    private ConfiguredObject<?> _parent;
    private HashMap<UUID, ConfiguredObjectRecord> _records;

    public ManagementModeStoreHandler(DurableConfigurationStore store,
                                      SystemConfig<?> systemConfig)
    {
        _systemConfig = systemConfig;
        _store = store;
    }

    @Override
    public void openConfigurationStore(final ConfiguredObject<?> parent,
                                       final boolean overwrite,
                                       final ConfiguredObjectRecord... initialRecords)
            throws StoreException
    {
        _parent = parent;
        _store.openConfigurationStore(parent, overwrite, initialRecords);

        _quiescedEntriesOriginalState = quiesceEntries(_systemConfig);


        _records = new HashMap<UUID, ConfiguredObjectRecord>();
        final ConfiguredObjectRecordHandler localRecoveryHandler = new ConfiguredObjectRecordHandler()
        {
            private int _version;
            private boolean _quiesceHttpPort = _systemConfig.getManagementModeHttpPortOverride() > 0;

            @Override
            public void begin()
            {
            }

            @Override
            public boolean handle(final ConfiguredObjectRecord object)
            {
                String entryType = object.getType();
                Map<String, Object> attributes = object.getAttributes();
                boolean quiesce = false;
                if (VIRTUAL_HOST_TYPE.equals(entryType) && _systemConfig.isManagementModeQuiesceVirtualHosts())
                {
                    quiesce = true;
                }
                else if (PORT_TYPE.equals(entryType))
                {
                    if (attributes == null)
                    {
                        throw new IllegalConfigurationException("Port attributes are not set in " + object);
                    }
                    Set<Protocol> protocols = getPortProtocolsAttribute(attributes);
                    if (protocols == null)
                    {
                        quiesce = true;
                    }
                    else
                    {
                        for (Protocol protocol : protocols)
                        {
                            switch (protocol)
                            {
                                case HTTP:
                                    quiesce = _quiesceHttpPort;
                                    break;
                                default:
                                    quiesce = true;
                            }
                        }
                    }
                }
                if (quiesce)
                {
                    LOGGER.debug("Management mode quiescing entry {}", object);

                    // save original state
                    _quiescedEntriesOriginalState.put(object.getId(), attributes.get(ATTRIBUTE_STATE));
                    Map<String,Object> modifiedAttributes = new HashMap<String, Object>(attributes);
                    modifiedAttributes.put(ATTRIBUTE_STATE, State.QUIESCED);
                    ConfiguredObjectRecord record = new ConfiguredObjectRecordImpl(object.getId(), object.getType(), modifiedAttributes, object.getParents());
                    _records.put(record.getId(), record);

                }
                else
                {
                    _records.put(object.getId(), object);
                }
                return true;
            }


            @Override
            public void end()
            {
            }
        };




        _store.visitConfiguredObjectRecords(localRecoveryHandler);

        _cliEntries = createPortsFromCommandLineOptions(_systemConfig);

        for(ConfiguredObjectRecord entry : _cliEntries.values())
        {
            _records.put(entry.getId(),entry);
        }


    }

    @Override
    public void upgradeStoreStructure() throws StoreException
    {
        _store.upgradeStoreStructure();
    }

    @Override
    public void visitConfiguredObjectRecords(final ConfiguredObjectRecordHandler recoveryHandler) throws StoreException
    {


        recoveryHandler.begin();

        for(ConfiguredObjectRecord record : _records.values())
        {
            if(!recoveryHandler.handle(record))
            {
                break;
            }
        }
        recoveryHandler.end();
    }


    @Override
    public void create(final ConfiguredObjectRecord object)
    {
        synchronized (_store)
        {
            _store.create(object);
        }
        _records.put(object.getId(), object);
    }

    @Override
    public void update(final boolean createIfNecessary, final ConfiguredObjectRecord... records) throws StoreException
    {
        synchronized (_store)
        {

            Collection<ConfiguredObjectRecord> actualUpdates = new ArrayList<ConfiguredObjectRecord>();

            for(ConfiguredObjectRecord record : records)
            {
                if (_cliEntries.containsKey(record.getId()))
                {
                    throw new IllegalConfigurationException("Cannot save configuration provided as command line argument:"
                                                            + record);
                }
                else if (_quiescedEntriesOriginalState.containsKey(record.getId()))
                {
                    // save entry with the original state
                    record = createEntryWithState(record, _quiescedEntriesOriginalState.get(record.getId()));
                }
                actualUpdates.add(record);
            }
            _store.update(createIfNecessary, actualUpdates.toArray(new ConfiguredObjectRecord[actualUpdates.size()]));
        }
        for(ConfiguredObjectRecord record : records)
        {
            _records.put(record.getId(), record);
        }
    }

    @Override
    public void closeConfigurationStore() throws StoreException
    {
    }

    @Override
    public void onDelete(ConfiguredObject<?> parent)
    {
    }

    @Override
    public synchronized UUID[] remove(final ConfiguredObjectRecord... records)
    {
        synchronized (_store)
        {
            UUID[] idsToRemove = new UUID[records.length];
            for(int i = 0; i < records.length; i++)
            {
                idsToRemove[i] = records[i].getId();
            }

            for (UUID id : idsToRemove)
            {
                if (_cliEntries.containsKey(id))
                {
                    throw new IllegalConfigurationException("Cannot change configuration for command line entry:"
                                                            + _cliEntries.get(id));
                }
            }
            UUID[] result = _store.remove(records);
            for (UUID id : idsToRemove)
            {
                if (_quiescedEntriesOriginalState.containsKey(id))
                {
                    _quiescedEntriesOriginalState.remove(id);
                }
            }
            for(ConfiguredObjectRecord record : records)
            {
                _records.remove(record.getId());
            }
            return result;
        }
    }

    private Map<UUID, ConfiguredObjectRecord> createPortsFromCommandLineOptions(SystemConfig<?> options)
    {
        int managementModeHttpPortOverride = options.getManagementModeHttpPortOverride();
        if (managementModeHttpPortOverride < 0)
        {
            throw new IllegalConfigurationException("Invalid http port is specified: " + managementModeHttpPortOverride);
        }
        Map<UUID, ConfiguredObjectRecord> cliEntries = new HashMap<UUID, ConfiguredObjectRecord>();
        if (managementModeHttpPortOverride != 0)
        {
            ConfiguredObjectRecord entry = createCLIPortEntry(managementModeHttpPortOverride, Protocol.HTTP);
            cliEntries.put(entry.getId(), entry);
        }
        return cliEntries;
    }

    private ConfiguredObjectRecord createCLIPortEntry(int port, Protocol protocol)
    {
        ConfiguredObjectRecord parent = findBroker();

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Port.PORT, port);
        attributes.put(Port.PROTOCOLS, Collections.singleton(protocol));
        attributes.put(Port.NAME, MANAGEMENT_MODE_PORT_PREFIX + protocol.name());
        attributes.put(Port.AUTHENTICATION_PROVIDER, BrokerAdapter.MANAGEMENT_MODE_AUTHENTICATION);
        ConfiguredObjectRecord portEntry = new ConfiguredObjectRecordImpl(UUID.randomUUID(), PORT_TYPE, attributes,
                Collections.singletonMap(parent.getType(),parent.getId()));
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Add management mode port configuration " + portEntry + " for port " + port + " and protocol "
                    + protocol);
        }
        return portEntry;
    }

    private ConfiguredObjectRecord findBroker()
    {
        for(ConfiguredObjectRecord record : _records.values())
        {
            if(record.getType().equals(Broker.class.getSimpleName()))
            {
                return record;
            }
        }
        return null;
    }


    private Map<UUID, Object> quiesceEntries(final SystemConfig<?> options)
    {
        final Map<UUID, Object> quiescedEntries = new HashMap<UUID, Object>();
        final int managementModeHttpPortOverride = options.getManagementModeHttpPortOverride();

        _store.visitConfiguredObjectRecords(new ConfiguredObjectRecordHandler()
        {
            @Override
            public void begin()
            {

            }

            @Override
            public boolean handle(final ConfiguredObjectRecord entry)
            {
                String entryType = entry.getType();
                Map<String, Object> attributes = entry.getAttributes();
                boolean quiesce = false;
                if (VIRTUAL_HOST_TYPE.equals(entryType) && options.isManagementModeQuiesceVirtualHosts())
                {
                    quiesce = true;
                }
                else if (PORT_TYPE.equals(entryType))
                {
                    if (attributes == null)
                    {
                        throw new IllegalConfigurationException("Port attributes are not set in " + entry);
                    }
                    Set<Protocol> protocols = getPortProtocolsAttribute(attributes);
                    if (protocols == null)
                    {
                        quiesce = true;
                    }
                    else
                    {
                        for (Protocol protocol : protocols)
                        {
                            switch (protocol)
                            {
                                case HTTP:
                                    quiesce = managementModeHttpPortOverride > 0;
                                    break;
                                default:
                                    quiesce = true;
                            }
                        }
                    }
                }
                if (quiesce)
                {
                    LOGGER.debug("Management mode quiescing entry {}", entry);

                    // save original state
                    quiescedEntries.put(entry.getId(), attributes.get(ATTRIBUTE_STATE));
                }
                return true;
            }


            @Override
            public void end()
            {
            }
        });


        return quiescedEntries;
    }

    private Set<Protocol> getPortProtocolsAttribute(Map<String, Object> attributes)
    {
        Object object = attributes.get(Port.PROTOCOLS);
        if (object == null)
        {
            return null;
        }
        Model model = _parent.getModel();
        ConfiguredObjectTypeRegistry typeRegistry = model.getTypeRegistry();
        Map<String, ConfiguredObjectAttribute<?, ?>> attributeTypes =
                typeRegistry.getAttributeTypes(Port.class);
        ConfiguredObjectAttribute protocolsAttribute = attributeTypes.get(Port.PROTOCOLS);
        return (Set<Protocol>) protocolsAttribute.convert(object,_parent);

    }

    private ConfiguredObjectRecord createEntryWithState(ConfiguredObjectRecord entry, Object state)
    {
        Map<String, Object> attributes = new HashMap<String, Object>(entry.getAttributes());
        if (state == null)
        {
            attributes.remove(ATTRIBUTE_STATE);
        }
        else
        {
            attributes.put(ATTRIBUTE_STATE, state);
        }
        return new ConfiguredObjectRecordImpl(entry.getId(), entry.getType(), attributes, entry.getParents());
    }

}
