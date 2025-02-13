/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;

/**
 * Immutable representation of the state of dependency resolution. Can represent the result of resolving build
 * dependencies or the result of a full dependency graph resolution.
 *
 * <p> In case of failures, both fatal and partial, exceptions are attached to the {@link VisitedGraphResults}. <p>
 */
public interface ResolverResults {
    /**
     * Returns the old model, which has been replaced by {@link VisitedGraphResults} and {@link VisitedArtifactSet}.
     * Using this model directly should be avoided.
     * This method should only be used to implement existing public API methods.
     *
     * @throws IllegalStateException if only build dependencies have been resolved.
     */
    ResolvedConfiguration getResolvedConfiguration();

    /**
     * Return the model representing the resolved graph. This model provides access
     * to the root component as well as any failure that occurred while resolving the graph.
     */
    VisitedGraphResults getVisitedGraph();

    /**
     * Returns details of the artifacts visited during dependency graph resolution. This set is later refined during artifact resolution.
     */
    VisitedArtifactSet getVisitedArtifacts();

    /**
     * Returns true if the full graph was resolved. False if only build dependencies were resolved.
     */
    boolean isFullyResolved();
}
