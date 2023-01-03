/*
 *  Copyright 2023 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.gradle;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Provider;
import org.teavm.gradle.config.ArtifactCoordinates;

public class TeaVMBaseExtensionImpl implements TeaVMBaseExtension {
    protected Project project;
    private Provider<Properties> properties;

    @Inject
    public TeaVMBaseExtensionImpl(Project project) {
        this.project = project;
        properties = project.provider(() -> {
            var result = new Properties();
            var p = project;
            while (p != null) {
                var dir = p.getProjectDir();
                append(result, new File(dir, "teavm-local.properties"));
                append(result, new File(dir, "teavm.properties"));
                p = p.getParent();
            }
            return result;
        });
    }

    private void append(Properties target, File source) throws IOException {
        if (!source.isFile()) {
            return;
        }
        var props = new Properties();
        try (var input = new FileReader(source, StandardCharsets.UTF_8)) {
            props.load(input);
        }
        append(target, props);
    }

    private void append(Properties target, Properties source) {
        for (var key : source.stringPropertyNames()) {
            if (!target.containsKey(key)) {
                target.setProperty(key, source.getProperty(key));
            }
        }
    }

    @Override
    public TeaVMLibraries getLibs() {
        return libs;
    }

    private TeaVMLibraries libs = new TeaVMLibraries() {
        @Override
        public Dependency getJso() {
            return project.getDependencies().create(ArtifactCoordinates.JSO);
        }

        @Override
        public Dependency getJsoApis() {
            return project.getDependencies().create(ArtifactCoordinates.JSO_APIS);
        }

        @Override
        public Dependency getInterop() {
            return project.getDependencies().create(ArtifactCoordinates.INTEROP);
        }

        @Override
        public Dependency getMetaprogramming() {
            return project.getDependencies().create(ArtifactCoordinates.METAPROGRAMMING);
        }
    };

    @Override
    public Provider<String> property(String name) {
        return properties.map(p -> {
            var result = p.getProperty(name);
            if (result != null) {
                return result;
            }
            return project.getProviders().gradleProperty("teavm." + name).getOrElse(null);
        });
    }
}
