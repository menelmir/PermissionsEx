/*
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ca.stellardrift.permissionsex.config;

import ca.stellardrift.permissionsex.backend.DataStore;
import ca.stellardrift.permissionsex.exception.PEBKACException;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMapper;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.Setting;
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializers;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static ca.stellardrift.permissionsex.Messages.*;

/**
 * Configuration for PermissionsEx. This is designed to be serialized with a Configurate {@link ObjectMapper}
 */
@ConfigSerializable
public class FilePermissionsExConfiguration<T> implements PermissionsExConfiguration<T> {
    static {
        TypeSerializers.getDefaultSerializers()
                .registerType(TypeToken.of(DataStore.class), new DataStoreSerializer())
                .registerType(new TypeToken<Supplier<?>>() {}, SupplierSerializer.INSTANCE);
    }


    private final ConfigurationLoader<?> loader;
    private final ConfigurationNode node;
    @Setting private Map<String, DataStore> backends;
    @Setting("default-backend") private String defaultBackend;
    @Setting private boolean debug;
    @Setting("server-tags") private List<String> serverTags;

    private final Class<T> platformConfigClass;
    private T platformConfig;

    protected FilePermissionsExConfiguration(ConfigurationLoader<?> loader, ConfigurationNode node, Class<T> platformConfigClass) {
        this.loader = loader;
        this.node = node;
        this.platformConfigClass = platformConfigClass;
    }

    public static FilePermissionsExConfiguration<?> fromLoader(ConfigurationLoader<?> loader) throws IOException {
        return fromLoader(loader, EmptyPlatformConfiguration.class);
    }

    public static <T> FilePermissionsExConfiguration<T> fromLoader(ConfigurationLoader<?> loader, Class<T> platformConfigClass) throws IOException {
        ConfigurationNode node = loader.load();
        ConfigurationNode fallbackConfig;
        try {
            fallbackConfig = FilePermissionsExConfiguration.loadDefaultConfiguration();
        } catch (IOException e) {
            throw new Error("PEX's default configuration could not be loaded!", e);
        }
        ConfigTransformations.versions().apply(node);
        node.mergeValuesFrom(fallbackConfig);
        ConfigurationNode defBackendNode = node.getNode("default-backend");
        if (defBackendNode.isVirtual() || defBackendNode.getValue() == null) { // Set based on whether or not the H2 backend is available
            defBackendNode.setValue("default-file");
            /*try {
                Class.forName("org.h2.Driver");
                defBackendNode.setValue("default");
            } catch (ClassNotFoundException e) {
                defBackendNode.setValue("default-file");
            }*/
        }

        FilePermissionsExConfiguration<T> config = new FilePermissionsExConfiguration<>(loader, node, platformConfigClass);
        config.load();
        return config;
    }

    private void load() throws IOException {
        try {
            ObjectMapper.forObject(this).populate(this.node);
            this.platformConfig = ObjectMapper.forClass(this.platformConfigClass).bindToNew().populate(this.getPlatformConfigNode());
        } catch (ObjectMappingException e) {
            throw new IOException(e);
        }
        this.loader.save(node);
    }

    @Override
    public void save() throws IOException {
        try {
            ObjectMapper.forObject(this).serialize(this.node);
            ObjectMapper.forClass(this.platformConfigClass).bind(this.platformConfig).serialize(getPlatformConfigNode());
        } catch (ObjectMappingException e) {
            throw new IOException(e);
        }

        this.loader.save(node);
    }

    private ConfigurationNode getPlatformConfigNode() {
        return this.node.getNode("platform");
    }

    @Override
    public DataStore getDataStore(String name) {
        return backends.get(name);
    }

    @Override
    public DataStore getDefaultDataStore() {
        return backends.get(defaultBackend);
    }

    @Override
    public boolean isDebugEnabled() {
        return debug;
    }

    @Override
    public List<String> getServerTags() {
        return Collections.unmodifiableList(serverTags);
    }

    @Override
    public void validate() throws PEBKACException {
        if (backends.isEmpty()) {
            throw new PEBKACException(CONFIG_ERROR_NO_BACKENDS.get());
        }
        if (defaultBackend == null) {
            throw new PEBKACException(CONFIG_ERROR_NO_DEFAULT.get());
        }

        if (!backends.containsKey(defaultBackend)) {
            throw new PEBKACException(CONFIG_ERROR_INVALID_DEFAULT.get(defaultBackend, backends.keySet()));
        }
    }

    @Override
    public T getPlatformConfig() {
        return this.platformConfig;
    }

    @Override
    public FilePermissionsExConfiguration<T> reload() throws IOException {
        ConfigurationNode node = this.loader.load();
        FilePermissionsExConfiguration<T> ret = new FilePermissionsExConfiguration<T>(this.loader, node, this.platformConfigClass);
        ret.load();
        return ret;
    }

    public static ConfigurationNode loadDefaultConfiguration() throws IOException {
        final URL defaultConfig = FilePermissionsExConfiguration.class.getResource("default.conf");
        if (defaultConfig == null) {
            throw new Error(CONFIG_ERROR_DEFAULT_CONFIG.get().translate(Locale.getDefault()));
        }
        HoconConfigurationLoader fallbackLoader = HoconConfigurationLoader.builder().setURL(defaultConfig).build();
        return fallbackLoader.load();

    }
}
