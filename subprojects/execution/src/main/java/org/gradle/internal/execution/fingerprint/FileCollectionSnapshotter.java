/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.fingerprint;

import org.gradle.api.file.FileCollection;
import org.gradle.internal.snapshot.FileSystemSnapshot;

/**
 * Service for snapshotting {@link FileCollection}s.
 */
public interface FileCollectionSnapshotter {
    interface Result {
        FileSystemSnapshot getSnapshot();

        /**
         * Whether or not the snapshotted file collection consists only of file trees.
         *
         * If the file collection does not contain any file trees, then this will return {@code false}.
         */
        boolean isFileTreeOnly();
    }

    /**
     * Returns snapshots of the roots of a file collection.
     */
    FileSystemSnapshot snapshot(FileCollection fileCollection);

    Result snapshotResult(FileCollection fileCollection);
}
