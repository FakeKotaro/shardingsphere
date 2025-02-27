/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.encrypt.subscriber;

import com.google.common.eventbus.Subscribe;
import lombok.Setter;
import org.apache.shardingsphere.encrypt.api.config.EncryptRuleConfiguration;
import org.apache.shardingsphere.encrypt.event.encryptor.AlterEncryptorEvent;
import org.apache.shardingsphere.encrypt.event.encryptor.DropEncryptorEvent;
import org.apache.shardingsphere.encrypt.rule.EncryptRule;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.util.yaml.YamlEngine;
import org.apache.shardingsphere.infra.yaml.config.pojo.algorithm.YamlAlgorithmConfiguration;
import org.apache.shardingsphere.infra.yaml.config.swapper.algorithm.YamlAlgorithmConfigurationSwapper;
import org.apache.shardingsphere.mode.event.config.DatabaseRuleConfigurationChangedEvent;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.mode.subsciber.RuleChangedSubscriber;

import java.util.LinkedHashMap;
import java.util.LinkedList;

/**
 * Encryptor subscriber.
 */
@SuppressWarnings("UnstableApiUsage")
@Setter
public final class EncryptorSubscriber implements RuleChangedSubscriber {
    
    private ContextManager contextManager;
    
    /**
     * Renew with alter encryptor.
     *
     * @param event alter encryptor event
     */
    @Subscribe
    public synchronized void renew(final AlterEncryptorEvent event) {
        if (!event.getActiveVersion().equals(contextManager.getInstanceContext().getModeContextManager().getActiveVersionByKey(event.getActiveVersionKey()))) {
            return;
        }
        String yamlContent = contextManager.getInstanceContext().getModeContextManager().getVersionPathByActiveVersionKey(event.getActiveVersionKey(), event.getActiveVersion());
        AlgorithmConfiguration beBeChangedConfig = new YamlAlgorithmConfigurationSwapper().swapToObject(YamlEngine.unmarshal(yamlContent, YamlAlgorithmConfiguration.class));
        ShardingSphereDatabase database = contextManager.getMetaDataContexts().getMetaData().getDatabases().get(event.getDatabaseName());
        EncryptRuleConfiguration config = database.getRuleMetaData().findSingleRule(EncryptRule.class)
                .map(optional -> getEncryptRuleConfiguration((EncryptRuleConfiguration) optional.getConfiguration()))
                .orElseGet(() -> new EncryptRuleConfiguration(new LinkedList<>(), new LinkedHashMap<>()));
        config.getEncryptors().put(event.getItemName(), beBeChangedConfig);
        contextManager.getInstanceContext().getEventBusContext().post(new DatabaseRuleConfigurationChangedEvent(event.getDatabaseName(), config));
    }
    
    /**
     * Renew with drop encryptor.
     *
     * @param event drop encryptor event
     */
    @Subscribe
    public synchronized void renew(final DropEncryptorEvent event) {
        if (!contextManager.getMetaDataContexts().getMetaData().containsDatabase(event.getDatabaseName())) {
            return;
        }
        ShardingSphereDatabase database = contextManager.getMetaDataContexts().getMetaData().getDatabases().get(event.getDatabaseName());
        EncryptRuleConfiguration config = (EncryptRuleConfiguration) database.getRuleMetaData().getSingleRule(EncryptRule.class).getConfiguration();
        config.getEncryptors().remove(event.getItemName());
        contextManager.getInstanceContext().getEventBusContext().post(new DatabaseRuleConfigurationChangedEvent(event.getDatabaseName(), config));
    }
    
    private EncryptRuleConfiguration getEncryptRuleConfiguration(final EncryptRuleConfiguration result) {
        return null == result.getEncryptors() ? new EncryptRuleConfiguration(result.getTables(), new LinkedHashMap<>()) : result;
    }
}
