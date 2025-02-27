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

package org.apache.shardingsphere.mask.subscriber;

import com.google.common.eventbus.Subscribe;
import lombok.Setter;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.util.yaml.YamlEngine;
import org.apache.shardingsphere.infra.yaml.config.pojo.algorithm.YamlAlgorithmConfiguration;
import org.apache.shardingsphere.infra.yaml.config.swapper.algorithm.YamlAlgorithmConfigurationSwapper;
import org.apache.shardingsphere.mask.api.config.MaskRuleConfiguration;
import org.apache.shardingsphere.mask.event.algorithm.AlterMaskAlgorithmEvent;
import org.apache.shardingsphere.mask.event.algorithm.DropMaskAlgorithmEvent;
import org.apache.shardingsphere.mask.rule.MaskRule;
import org.apache.shardingsphere.mode.event.config.DatabaseRuleConfigurationChangedEvent;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.mode.subsciber.RuleChangedSubscriber;

import java.util.LinkedHashMap;
import java.util.LinkedList;

/**
 * Mask algorithm subscriber.
 */
@SuppressWarnings("UnstableApiUsage")
@Setter
public final class MaskAlgorithmSubscriber implements RuleChangedSubscriber {
    
    private ContextManager contextManager;
    
    /**
     * Renew with alter mask algorithm.
     *
     * @param event alter mask algorithm event
     */
    @Subscribe
    public synchronized void renew(final AlterMaskAlgorithmEvent event) {
        if (!event.getActiveVersion().equals(contextManager.getInstanceContext().getModeContextManager().getActiveVersionByKey(event.getActiveVersionKey()))) {
            return;
        }
        String yamlContent = contextManager.getInstanceContext().getModeContextManager().getVersionPathByActiveVersionKey(event.getActiveVersionKey(), event.getActiveVersion());
        AlgorithmConfiguration toBeChangedConfig = new YamlAlgorithmConfigurationSwapper().swapToObject(YamlEngine.unmarshal(yamlContent, YamlAlgorithmConfiguration.class));
        ShardingSphereDatabase database = contextManager.getMetaDataContexts().getMetaData().getDatabases().get(event.getDatabaseName());
        MaskRuleConfiguration config = database.getRuleMetaData().findSingleRule(MaskRule.class)
                .map(maskRule -> getConfiguration((MaskRuleConfiguration) maskRule.getConfiguration())).orElseGet(() -> new MaskRuleConfiguration(new LinkedList<>(), new LinkedHashMap<>()));
        config.getMaskAlgorithms().put(event.getItemName(), toBeChangedConfig);
        contextManager.getInstanceContext().getEventBusContext().post(new DatabaseRuleConfigurationChangedEvent(event.getDatabaseName(), config));
    }
    
    /**
     * Renew with drop mask algorithm.
     *
     * @param event drop mask algorithm event
     */
    @Subscribe
    public synchronized void renew(final DropMaskAlgorithmEvent event) {
        if (!contextManager.getMetaDataContexts().getMetaData().containsDatabase(event.getDatabaseName())) {
            return;
        }
        ShardingSphereDatabase database = contextManager.getMetaDataContexts().getMetaData().getDatabases().get(event.getDatabaseName());
        MaskRuleConfiguration config = (MaskRuleConfiguration) database.getRuleMetaData().getSingleRule(MaskRule.class).getConfiguration();
        config.getMaskAlgorithms().remove(event.getItemName());
        contextManager.getInstanceContext().getEventBusContext().post(new DatabaseRuleConfigurationChangedEvent(event.getDatabaseName(), config));
    }
    
    private MaskRuleConfiguration getConfiguration(final MaskRuleConfiguration config) {
        return null == config.getMaskAlgorithms() ? new MaskRuleConfiguration(config.getTables(), new LinkedHashMap<>()) : config;
    }
}
