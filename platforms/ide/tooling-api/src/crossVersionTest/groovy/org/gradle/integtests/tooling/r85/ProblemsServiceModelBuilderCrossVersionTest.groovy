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

package org.gradle.integtests.tooling.r85

import groovy.json.JsonSlurper
import org.gradle.api.problems.Problems
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.BaseProblemDescriptor
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.problems.ProblemEvent
import org.junit.Assume

@ToolingApiVersion(">=8.5")
@TargetGradleVersion(">=8.5")
class ProblemsServiceModelBuilderCrossVersionTest extends ToolingApiSpecification {

    def withSampleProject(boolean includeAdditionalMetadata = false, boolean publicApi = false) {
        buildFile << """
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import ${Problems.name}
            import javax.inject.Inject

            allprojects {
                apply plugin: CustomPlugin
            }

            class CustomModel implements Serializable {
                CustomModel() {
                }
            }

            class CustomBuilder implements ToolingModelBuilder {
                private final Problems problemsService

                CustomBuilder(Problems problemsService) {
                    this.problemsService = problemsService
                }

                boolean canBuild(String modelName) {
                    return modelName == '${CustomModel.name}'
                }
                Object buildAll(String modelName, Project project) {
                    problemsService.${publicApi ? "forNamespace(\"org.example.plugin\").reporting" : "create"} {
                        it.label("label")
                            .category("testcategory")
                            .withException(new RuntimeException("test"))
                            ${if (includeAdditionalMetadata) { ".additionalData(\"keyToString\", \"value\")" }}
                    }${publicApi ? "" : ".report()"}
                    return new CustomModel()
                }
            }

            class CustomPlugin implements Plugin<Project> {
                @Inject
                CustomPlugin(ToolingModelBuilderRegistry registry, Problems problemsService) {
                    registry.register(new CustomBuilder(problemsService))
                }

                public void apply(Project project) {
                }
            }
        """
    }

    @TargetGradleVersion(">=8.6")
    @ToolingApiVersion(">=8.6")
    def "Can use problems service in model builder"() {
        given:
        Assume.assumeTrue(javaHome != null)
        withSampleProject(false, true)
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection {
            it.model(CustomModel)
                .setJavaHome(javaHome.javaHome)
                .addProgressListener(listener)
                .get()
        }
        def problems = listener.problems.collect{ it as ProblemDescriptor}

        then:
        problems.size() == 1
        problems[0].label.label == 'label'
        problems[0].category.category == 'testcategory'
//        problems[0].exception.exception.message == 'test'

        where:
        javaHome << [
            AvailableJavaHomes.jdk8,
            AvailableJavaHomes.jdk17,
            AvailableJavaHomes.jdk21
        ]
    }

    @ToolingApiVersion("<8.6")
    @TargetGradleVersion("<8.6")
    def "Can use problems service in model builder before 8"() {
        given:
        withSampleProject()
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection {
            it.model(CustomModel)
                .setJavaHome(javaHome)
                .addProgressListener(listener)
                .get()
        }
        def problems = listener.problems.collect { new JsonSlurper().parse(listener.problems[0].json.bytes) }

        then:
        problems.size() == 1
        problems[0].label == 'label'
        problems[0].problemCategory == 'testcategory'
    }

    @ToolingApiVersion(">=8.6")
    @TargetGradleVersion(">=8.6")
    def "Can add additional metadata"() {
        given:
        withSampleProject(true, true)
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection { connection ->
            connection.model(CustomModel)
                .addProgressListener(listener)
                .get()
        }

        then:
        listener.problems.size() == 1
        listener.problems[0].additionalData.asMap == [
            'keyToString': 'value'
        ]
    }

    class ProblemProgressListener implements ProgressListener {

        List<BaseProblemDescriptor> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof ProblemEvent) {
                this.problems.add(event.getDescriptor())
            }
        }
    }
}
