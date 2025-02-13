/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.groovy.scripts.internal;

import groovy.lang.Script;
import org.codehaus.groovy.ast.ClassNode;
import org.gradle.api.Action;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Pair;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassData;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.classpath.transforms.ClassTransform;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.scripts.BuildScriptCompileUnitOfWork;
import org.gradle.model.dsl.internal.transform.RuleVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;

import static org.gradle.internal.classpath.CachedClasspathTransformer.StandardTransform.BuildLogic;

/**
 * A {@link ScriptClassCompiler} which compiles scripts to a cache directory, and loads them from there.
 */
public class GroovyScriptClassCompiler implements ScriptClassCompiler, Closeable {
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    private static final String CLASSPATH_PROPERTY_NAME = "classpath";
    private static final String TEMPLATE_ID_PROPERTY_NAME = "templateId";
    private static final String SOURCE_HASH_PROPERTY_NAME = "sourceHash";
    private final ScriptCompilationHandler scriptCompilationHandler;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final CachedClasspathTransformer classpathTransformer;
    private final ExecutionEngine earlyExecutionEngine;
    private final FileCollectionFactory fileCollectionFactory;
    private final InputFingerprinter inputFingerprinter;
    private final ImmutableWorkspaceProvider workspaceProvider;

    public GroovyScriptClassCompiler(
        ScriptCompilationHandler scriptCompilationHandler,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        CachedClasspathTransformer classpathTransformer,
        ExecutionEngine earlyExecutionEngine,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        ImmutableWorkspaceProvider workspaceProvider
    ) {
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.classpathTransformer = classpathTransformer;
        this.earlyExecutionEngine = earlyExecutionEngine;
        this.fileCollectionFactory = fileCollectionFactory;
        this.inputFingerprinter = inputFingerprinter;
        this.workspaceProvider = workspaceProvider;
    }

    @Override
    public <T extends Script, M> CompiledScript<T, M> compile(
        final ScriptSource source, final Class<T> scriptBaseClass, final Object target,
        final ClassLoaderScope targetScope,
        final CompileOperation<M> operation,
        final Action<? super ClassNode> verifier
    ) {
        assert source.getResource().isContentCached();
        if (source.getResource().getHasEmptyContent()) {
            return new EmptyCompiledScript<>(operation);
        }

        String templateId = operation.getId();
        // TODO: Figure if execution engine should calculate the source hash on its own
        HashCode sourceHashCode = source.getResource().getContentHash();
        RemappingScriptSource remapped = new RemappingScriptSource(source);
        ClassLoader classLoader = targetScope.getExportClassLoader();
        File outputDir = doCompile(target, templateId, sourceHashCode, remapped, classLoader, operation, verifier, scriptBaseClass);

        File genericClassesDir = classesDir(outputDir, operation);
        File metadataDir = metadataDir(outputDir);
        // TODO: Move instrumentation to the execution engine and remove the remapping or move remapping to the non-cacheable unit of work
        ClassPath remappedClasses = remapClasses(genericClassesDir, remapped);
        return scriptCompilationHandler.loadFromDir(source, sourceHashCode, targetScope, remappedClasses, metadataDir, operation, scriptBaseClass);
    }

    private <T extends Script> File doCompile(
        Object target,
        String templateId,
        HashCode sourceHashCode,
        RemappingScriptSource source,
        ClassLoader classLoader,
        CompileOperation<?> operation,
        Action<? super ClassNode> verifier,
        Class<T> scriptBaseClass
    ) {
        UnitOfWork unitOfWork = new GroovyScriptCompileUnitOfWork(
            templateId,
            sourceHashCode,
            classLoader,
            classLoaderHierarchyHasher,
            workspaceProvider,
            fileCollectionFactory,
            inputFingerprinter,
            workspace -> scriptCompilationHandler.compileToDir(source, classLoader, classesDir(workspace, operation), metadataDir(workspace), operation, scriptBaseClass, verifier)
        );
        return getExecutionEngine(target)
            .createRequest(unitOfWork)
            .execute()
            .getOutputAs(File.class)
            .get();
    }

    /**
     * We want to use build cache for script compilation, but build cache might not be available yet with early execution engine.
     * Thus settings and init scripts are not using build cache for now.<br/><br/>
     *
     * When we compile project build scripts, build cache is available, but we need to query execution engine with build cache support
     * from the project services directly to use it.<br/><br/>
     *
     * TODO: Remove this and just inject execution engine once we unify execution engines in https://github.com/gradle/gradle/issues/27249
     */
    private ExecutionEngine getExecutionEngine(Object target) {
        if (target instanceof ProjectInternal) {
            return ((ProjectInternal) target).getServices().get(ExecutionEngine.class);
        }
        return earlyExecutionEngine;
    }

    private ClassPath remapClasses(File genericClassesDir, RemappingScriptSource source) {
        ScriptSource origin = source.getSource();
        String className = origin.getClassName();
        return classpathTransformer.transform(DefaultClassPath.of(genericClassesDir), BuildLogic, new ClassTransform() {
            @Override
            public void applyConfigurationTo(Hasher hasher) {
                hasher.putString(GroovyScriptClassCompiler.class.getSimpleName());
                hasher.putInt(1); // transformation version
                hasher.putString(className);
            }

            @Override
            public Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor, ClassData classData) throws IOException {
                String renamed = entry.getPath().getLastName();
                if (renamed.startsWith(RemappingScriptSource.MAPPED_SCRIPT)) {
                    renamed = className + renamed.substring(RemappingScriptSource.MAPPED_SCRIPT.length());
                }
                byte[] content = entry.getContent();
                ClassReader cr = new ClassReader(content);
                String originalClassName = cr.getClassName();
                String contentHash = Hashing.hashBytes(content).toString();
                BuildScriptRemapper remapper = new BuildScriptRemapper(visitor, origin, originalClassName, contentHash);
                return Pair.of(entry.getPath().getParent().append(true, renamed), remapper);
            }
        });
    }

    @Override
    public void close() {
    }

    private File classesDir(File outputDir, CompileOperation<?> operation) {
        return new File(outputDir, operation.getId());
    }

    private File metadataDir(File outputDir) {
        return new File(outputDir, "metadata");
    }

    private static class GroovyScriptCompileUnitOfWork extends BuildScriptCompileUnitOfWork {

        private final String templateId;
        private final Consumer<File> compileAction;
        private final HashCode sourceHashCode;
        private final ClassLoader classLoader;
        private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;

        public GroovyScriptCompileUnitOfWork(
            String templateId,
            HashCode sourceHashCode,
            ClassLoader classLoader,
            ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
            ImmutableWorkspaceProvider workspaceProvider,
            FileCollectionFactory fileCollectionFactory,
            InputFingerprinter inputFingerprinter,
            Consumer<File> compileAction
        ) {
            super(workspaceProvider, fileCollectionFactory, inputFingerprinter);
            this.templateId = templateId;
            this.sourceHashCode = sourceHashCode;
            this.classLoader = classLoader;
            this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
            this.compileAction = compileAction;
        }

        @Override
        public void visitIdentityInputs(InputVisitor visitor) {
            visitor.visitInputProperty(TEMPLATE_ID_PROPERTY_NAME, () -> templateId);
            visitor.visitInputProperty(SOURCE_HASH_PROPERTY_NAME, () -> sourceHashCode);
            visitor.visitInputProperty(CLASSPATH_PROPERTY_NAME, () -> classLoaderHierarchyHasher.getClassLoaderHash(classLoader));
        }

        @Override
        public String getDisplayName() {
            return "Groovy DSL script compilation (" + templateId + ")";
        }

        @Override
        public void compileTo(File classesDir) {
            compileAction.accept(classesDir);
        }
    }

    private static class EmptyCompiledScript<T extends Script, M> implements CompiledScript<T, M> {
        private final M data;

        public EmptyCompiledScript(CompileOperation<M> operation) {
            this.data = operation.getExtractedData();
        }

        @Override
        public boolean getRunDoesSomething() {
            return false;
        }

        @Override
        public boolean getHasMethods() {
            return false;
        }

        @Override
        public void onReuse() {
            // Ignore
        }

        @Override
        public Class<? extends T> loadClass() {
            throw new UnsupportedOperationException("Cannot load a script that does nothing.");
        }

        @Override
        public M getData() {
            return data;
        }
    }

    private static class BuildScriptRemapper extends ClassVisitor implements Opcodes {
        private static final String SCRIPT_ORIGIN = "org/gradle/internal/scripts/ScriptOrigin";
        private final ScriptSource scriptSource;
        private final String originalClassName;
        private final String contentHash;

        public BuildScriptRemapper(ClassVisitor cv, ScriptSource source, String originalClassName, String contentHash) {
            super(AsmConstants.ASM_LEVEL, cv);
            this.scriptSource = source;
            this.originalClassName = originalClassName;
            this.contentHash = contentHash;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            String owner = remap(name);
            boolean shouldAddScriptOrigin = shouldAddScriptOrigin(access);
            cv.visit(version, access, owner, remap(signature), remap(superName), remapAndAddInterfaces(interfaces, shouldAddScriptOrigin));
            if (shouldAddScriptOrigin) {
                addOriginalClassName(cv, owner, originalClassName);
                addContentHash(cv, owner, contentHash);
            }
        }

        private static boolean shouldAddScriptOrigin(int access) {
            return ((access & ACC_INTERFACE) == 0) && ((access & ACC_ANNOTATION) == 0);
        }

        private static void addOriginalClassName(ClassVisitor cv, String owner, String originalClassName) {
            cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC | ACC_FINAL, "__originalClassName", Type.getDescriptor(String.class), "", originalClassName);
            MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "getOriginalClassName", Type.getMethodDescriptor(Type.getType(String.class)), null, null);
            mv.visitCode();
            mv.visitFieldInsn(GETSTATIC, owner, "__originalClassName", Type.getDescriptor(String.class));
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        private static void addContentHash(ClassVisitor cv, String owner, String contentHash) {
            cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC | ACC_FINAL, "__signature", Type.getDescriptor(String.class), "", contentHash);
            MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "getContentHash", Type.getMethodDescriptor(Type.getType(String.class)), null, null);
            mv.visitCode();
            mv.visitFieldInsn(GETSTATIC, owner, "__signature", Type.getDescriptor(String.class));
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        @Override
        public void visitSource(String source, String debug) {
            cv.visitSource(scriptSource.getFileName(), debug);
        }

        private String[] remapAndAddInterfaces(String[] interfaces, boolean shouldAddScriptOrigin) {
            if (!shouldAddScriptOrigin) {
                return remap(interfaces);
            }
            if (interfaces == null) {
                return new String[]{SCRIPT_ORIGIN};
            }
            String[] remapped = new String[interfaces.length + 1];
            for (int i = 0; i < interfaces.length; i++) {
                remapped[i] = remap(interfaces[i]);
            }
            remapped[remapped.length - 1] = SCRIPT_ORIGIN;
            return remapped;
        }

        private String[] remap(String[] names) {
            if (names == null) {
                return null;
            }
            String[] remapped = new String[names.length];
            for (int i = 0; i < names.length; i++) {
                remapped[i] = remap(names[i]);
            }
            return remapped;
        }

        private String remap(String name) {
            if (name == null) {
                return null;
            }
            if (RuleVisitor.SOURCE_URI_TOKEN.equals(name)) {
                URI uri = scriptSource.getResource().getLocation().getURI();
                return uri == null ? null : uri.toString();
            }
            if (RuleVisitor.SOURCE_DESC_TOKEN.equals(name)) {
                return scriptSource.getDisplayName();
            }
            return name.replace(RemappingScriptSource.MAPPED_SCRIPT, scriptSource.getClassName());
        }

        private Object remap(Object o) {
            if (o instanceof Type) {
                return Type.getType(remap(((Type) o).getDescriptor()));
            }
            if (o instanceof String) {
                return remap((String) o);
            }
            return o;
        }

        private Object[] remap(int count, Object[] original) {
            if (count == 0) {
                return EMPTY_OBJECT_ARRAY;
            }
            Object[] remapped = new Object[count];
            for (int idx = 0; idx < count; idx++) {
                remapped[idx] = remap(original[idx]);
            }
            return remapped;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, remap(desc), remap(signature), remap(exceptions));
            if (mv != null && (access & ACC_ABSTRACT) == 0) {
                mv = new MethodRenamer(mv);
            }
            return mv;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            return super.visitField(access, name, remap(desc), remap(signature), remap(value));
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            super.visitInnerClass(remap(name), remap(outerName), remap(innerName), access);
        }

        @Override
        public void visitOuterClass(String owner, String name, String desc) {
            super.visitOuterClass(remap(owner), remap(name), remap(desc));
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return super.visitAnnotation(remap(desc), visible);
        }

        class MethodRenamer extends MethodVisitor {

            public MethodRenamer(final MethodVisitor mv) {
                super(AsmConstants.ASM_LEVEL, mv);
            }

            @Override
            public void visitTypeInsn(int i, String name) {
                mv.visitTypeInsn(i, remap(name));
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                mv.visitFieldInsn(opcode, remap(owner), name, remap(desc));
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean intf) {
                mv.visitMethodInsn(opcode, remap(owner), name, remap(desc), intf);
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                mv.visitInvokeDynamicInsn(remap(name), remap(descriptor), bootstrapMethodHandle, bootstrapMethodArguments);
            }

            @Override
            public void visitLdcInsn(Object cst) {
                super.visitLdcInsn(remap(cst));
            }

            @Override
            public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                super.visitLocalVariable(name, remap(desc), remap(signature), start, end, index);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                return super.visitAnnotation(remap(desc), visible);
            }

            @Override
            public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
                super.visitFrame(type, nLocal, remap(nLocal, local), nStack, remap(nStack, stack));
            }
        }
    }
}
