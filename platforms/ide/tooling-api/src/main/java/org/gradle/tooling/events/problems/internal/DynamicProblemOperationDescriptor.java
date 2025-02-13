/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.events.problems.internal;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.problems.Details;
import org.gradle.tooling.events.problems.DocumentationLink;
import org.gradle.tooling.events.problems.Label;
import org.gradle.tooling.events.problems.Location;
import org.gradle.tooling.events.problems.ProblemCategory;
import org.gradle.tooling.events.problems.ProblemDescriptor;
import org.gradle.tooling.events.problems.Severity;
import org.gradle.tooling.events.problems.Solution;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;

import java.util.List;

@NonNullApi
public class DynamicProblemOperationDescriptor extends DefaultOperationDescriptor implements ProblemDescriptor {

    public DynamicProblemOperationDescriptor(
        InternalOperationDescriptor internalDescriptor,
        OperationDescriptor parent
    ) {
        super(internalDescriptor, parent);
    }

    @Override
    public ProblemCategory getCategory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Label getLabel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Details getDetails() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Severity getSeverity() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Location> getLocations() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Solution> getSolutions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocumentationLink getDocumentationLink() {
        throw new UnsupportedOperationException();
    }

//    @Nullable
//    @Override
//    public ExceptionContainer getException() {
//        throw new UnsupportedOperationException();
//    }
}
