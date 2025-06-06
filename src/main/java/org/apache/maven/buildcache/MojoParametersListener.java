/*
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
 */
package org.apache.maven.buildcache;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.maven.buildcache.checksum.MavenProjectInput;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.CacheState;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.MojoExecutionListener;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.maven.buildcache.xml.CacheState.DISABLED;
import static org.apache.maven.buildcache.xml.CacheState.INITIALIZED;

/**
 * MojoParametersListener
 */
@Singleton
@Named
public class MojoParametersListener implements MojoExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MojoParametersListener.class);

    @SuppressWarnings("checkstyle:LineLength")
    private final ConcurrentMap<MavenProject, Map<String, MojoExecutionEvent>> projectExecutions =
            new ConcurrentHashMap<>();

    private final CacheConfig cacheConfig;

    @Inject
    public MojoParametersListener(CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    @Override
    public void beforeMojoExecution(MojoExecutionEvent event) {
        final String executionKey = CacheUtils.mojoExecutionKey(event.getExecution());
        LOGGER.debug(
                "Starting mojo execution: {}, class: {}",
                executionKey,
                event.getMojo().getClass());
        final MavenProject project = event.getProject();
        CacheState cacheState = DISABLED;
        boolean cacheIsDisabled = MavenProjectInput.isCacheDisabled(project);
        if (!cacheIsDisabled) {
            cacheState = cacheConfig.initialize();
        }
        LOGGER.debug("cacheState: {}", cacheState);
        if (cacheState == INITIALIZED) {
            Map<String, MojoExecutionEvent> projectEvents = projectExecutions.get(project);
            if (projectEvents == null) {
                Map<String, MojoExecutionEvent> candidate = new ConcurrentHashMap<>();
                projectEvents = projectExecutions.putIfAbsent(project, candidate);
                if (projectEvents == null) {
                    projectEvents = candidate;
                }
            }
            projectEvents.put(executionKey, event);
        }
    }

    @Override
    public void afterMojoExecutionSuccess(MojoExecutionEvent event) throws MojoExecutionException {
        // do nothing
    }

    @Override
    public void afterExecutionFailure(MojoExecutionEvent event) {
        // do nothing
    }

    public Map<String, MojoExecutionEvent> getProjectExecutions(MavenProject project) {
        return projectExecutions.get(project);
    }
}
