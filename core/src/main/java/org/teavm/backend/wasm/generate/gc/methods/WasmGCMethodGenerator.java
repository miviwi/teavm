/*
 *  Copyright 2024 Alexey Andreev.
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
package org.teavm.backend.wasm.generate.gc.methods;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.teavm.ast.decompilation.Decompiler;
import org.teavm.backend.lowlevel.generate.NameProvider;
import org.teavm.backend.wasm.BaseWasmFunctionRepository;
import org.teavm.backend.wasm.WasmFunctionTypes;
import org.teavm.backend.wasm.gc.WasmGCMethodReturnTypes;
import org.teavm.backend.wasm.gc.WasmGCVariableCategoryProvider;
import org.teavm.backend.wasm.generate.gc.classes.WasmGCClassInfoProvider;
import org.teavm.backend.wasm.generate.gc.classes.WasmGCStandardClasses;
import org.teavm.backend.wasm.generate.gc.classes.WasmGCSupertypeFunctionProvider;
import org.teavm.backend.wasm.generate.gc.classes.WasmGCTypeMapper;
import org.teavm.backend.wasm.generate.gc.strings.WasmGCStringProvider;
import org.teavm.backend.wasm.generators.gc.WasmGCCustomGenerator;
import org.teavm.backend.wasm.generators.gc.WasmGCCustomGeneratorContext;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmModule;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.backend.wasm.model.expression.WasmFunctionReference;
import org.teavm.backend.wasm.model.expression.WasmReturn;
import org.teavm.backend.wasm.model.expression.WasmSetGlobal;
import org.teavm.diagnostics.Diagnostics;
import org.teavm.interop.Import;
import org.teavm.model.CallLocation;
import org.teavm.model.ClassHierarchy;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.analysis.ClassInitializerInfo;
import org.teavm.model.classes.VirtualTableProvider;
import org.teavm.model.util.RegisterAllocator;

public class WasmGCMethodGenerator implements BaseWasmFunctionRepository {
    private WasmModule module;
    private ClassHierarchy hierarchy;
    private ClassHolderSource classes;
    private VirtualTableProvider virtualTables;
    private ClassInitializerInfo classInitInfo;
    private WasmFunctionTypes functionTypes;
    private WasmGCSupertypeFunctionProvider supertypeFunctions;
    private NameProvider names;
    private Diagnostics diagnostics;
    private WasmGCTypeMapper typeMapper;
    private WasmGCCustomGeneratorProvider customGenerators;
    private Queue<Runnable> queue = new ArrayDeque<>();
    private Map<MethodReference, WasmFunction> staticMethods = new HashMap<>();
    private Map<MethodReference, WasmFunction> instanceMethods = new HashMap<>();
    private boolean friendlyToDebugger;
    private Decompiler decompiler;
    private WasmGCGenerationContext context;
    private WasmFunction dummyInitializer;
    private WasmGCClassInfoProvider classInfoProvider;
    private WasmGCStandardClasses standardClasses;
    private WasmGCStringProvider strings;
    private WasmGCMethodReturnTypes returnTypes;

    public WasmGCMethodGenerator(
            WasmModule module,
            ClassHierarchy hierarchy,
            ClassHolderSource classes,
            VirtualTableProvider virtualTables,
            ClassInitializerInfo classInitInfo,
            WasmFunctionTypes functionTypes,
            NameProvider names,
            Diagnostics diagnostics,
            WasmGCMethodReturnTypes returnTypes,
            WasmGCCustomGeneratorProvider customGenerators
    ) {
        this.module = module;
        this.hierarchy = hierarchy;
        this.classes = classes;
        this.virtualTables = virtualTables;
        this.classInitInfo = classInitInfo;
        this.functionTypes = functionTypes;
        this.names = names;
        this.diagnostics = diagnostics;
        this.returnTypes = returnTypes;
        this.customGenerators = customGenerators;
    }

    public void setTypeMapper(WasmGCTypeMapper typeMapper) {
        this.typeMapper = typeMapper;
    }

    public void setFriendlyToDebugger(boolean friendlyToDebugger) {
        this.friendlyToDebugger = friendlyToDebugger;
    }

    public void setClassInfoProvider(WasmGCClassInfoProvider classInfoProvider) {
        this.classInfoProvider = classInfoProvider;
    }

    public void setStandardClasses(WasmGCStandardClasses standardClasses) {
        this.standardClasses = standardClasses;
    }

    public void setSupertypeFunctions(WasmGCSupertypeFunctionProvider supertypeFunctions) {
        this.supertypeFunctions = supertypeFunctions;
    }

    public void setStrings(WasmGCStringProvider strings) {
        this.strings = strings;
    }

    public boolean process() {
        if (queue.isEmpty()) {
            return false;
        }
        while (!queue.isEmpty()) {
            queue.remove().run();
        }
        return true;
    }

    @Override
    public WasmFunction forStaticMethod(MethodReference methodReference) {
        return staticMethods.computeIfAbsent(methodReference, this::createStaticFunction);
    }

    private WasmFunction createStaticFunction(MethodReference methodReference) {
        var returnType = typeMapper.mapType(returnTypes.returnTypeOf(methodReference));
        var parameterTypes = new WasmType[methodReference.parameterCount()];
        for (var i = 0; i < parameterTypes.length; ++i) {
            parameterTypes[i] = typeMapper.mapType(methodReference.parameterType(i));
        }
        var function = new WasmFunction(functionTypes.of(returnType, parameterTypes));
        function.setName(names.forMethod(methodReference));
        module.functions.add(function);
        function.setJavaMethod(methodReference);

        var cls = classes.get(methodReference.getClassName());
        if (cls != null) {
            var method = cls.getMethod(methodReference.getDescriptor());
            if (method != null && method.hasModifier(ElementModifier.STATIC)) {
                queue.add(() -> generateMethodBody(method, function));
            }
        }

        return function;
    }

    @Override
    public WasmFunction forInstanceMethod(MethodReference methodReference) {
        return instanceMethods.computeIfAbsent(methodReference, this::createInstanceFunction);
    }

    private WasmFunction createInstanceFunction(MethodReference methodReference) {
        var returnType = typeMapper.mapType(returnTypes.returnTypeOf(methodReference));
        var parameterTypes = new WasmType[methodReference.parameterCount() + 1];
        parameterTypes[0] = typeMapper.mapType(ValueType.object(methodReference.getClassName()));
        for (var i = 0; i < methodReference.parameterCount(); ++i) {
            parameterTypes[i + 1] = typeMapper.mapType(methodReference.parameterType(i));
        }
        var function = new WasmFunction(functionTypes.of(returnType, parameterTypes));
        function.setName(names.forMethod(methodReference));
        module.functions.add(function);
        function.setJavaMethod(methodReference);

        var cls = classes.get(methodReference.getClassName());
        if (cls != null) {
            var method = cls.getMethod(methodReference.getDescriptor());
            if (method != null && !method.hasModifier(ElementModifier.STATIC)) {
                queue.add(() -> generateMethodBody(method, function));
            }
        }

        return function;
    }

    private void generateMethodBody(MethodHolder method, WasmFunction function) {
        var customGenerator = customGenerators.get(method.getReference());
        if (customGenerator != null) {
            generateCustomMethodBody(customGenerator, method.getReference(), function);
        } else if (!method.hasModifier(ElementModifier.NATIVE)) {
            generateRegularMethodBody(method, function);
        } else {
            generateNativeMethodBody(method, function);
        }
    }

    private void generateCustomMethodBody(WasmGCCustomGenerator customGenerator, MethodReference method,
            WasmFunction function) {
        customGenerator.apply(method, function, customGeneratorContext);
    }

    private void generateRegularMethodBody(MethodHolder method, WasmFunction function) {
        var decompiler = getDecompiler();
        var categoryProvider = new WasmGCVariableCategoryProvider(hierarchy, returnTypes);
        var allocator = new RegisterAllocator(categoryProvider);
        allocator.allocateRegisters(method.getReference(), method.getProgram(), friendlyToDebugger);
        var ast = decompiler.decompileRegular(method);
        var firstVar = method.hasModifier(ElementModifier.STATIC) ? 1 : 0;
        var typeInference = categoryProvider.getTypeInference();

        var registerCount = 0;
        for (var i = 0; i < method.getProgram().variableCount(); ++i) {
            registerCount = Math.max(registerCount, method.getProgram().variableAt(i).getRegister() + 1);
        }
        var originalIndexToIndex = new int[registerCount];
        Arrays.fill(originalIndexToIndex, -1);
        for (var varNode : ast.getVariables()) {
            originalIndexToIndex[varNode.getOriginalIndex()] = varNode.getIndex();
        }

        var variableRepresentatives = new int[registerCount];
        Arrays.fill(variableRepresentatives, -1);
        for (var i = 0; i < method.getProgram().variableCount(); ++i) {
            var variable = method.getProgram().variableAt(i);
            var varNodeIndex = variable.getRegister() >= 0 ? originalIndexToIndex[variable.getRegister()] : -1;
            if (varNodeIndex >= 0 && variableRepresentatives[varNodeIndex] < 0) {
                variableRepresentatives[varNodeIndex] = variable.getIndex();
            }
        }

        for (var i = firstVar; i < ast.getVariables().size(); ++i) {
            var localVar = ast.getVariables().get(i);
            var representative = method.getProgram().variableAt(variableRepresentatives[i]);
            var inferredType = typeInference.typeOf(representative);
            var type = !inferredType.isArrayUnwrap
                    ? typeMapper.mapType(inferredType.valueType)
                    : classInfoProvider.getClassInfo(inferredType.valueType).getArray().getReference();
            var wasmLocal = new WasmLocal(type, localVar.getName());
            function.add(wasmLocal);
        }

        addInitializerErase(method, function);
        var visitor = new WasmGCGenerationVisitor(getGenerationContext(), function, firstVar, false);
        visitor.generate(ast.getBody(), function.getBody());
    }

    private void generateNativeMethodBody(MethodHolder method, WasmFunction function) {
        var importAnnot = method.getAnnotations().get(Import.class.getName());
        if (importAnnot == null) {
            diagnostics.error(new CallLocation(method.getReference()), "Method is not annotated with {{c0}}",
                    Import.class.getName());
            return;
        }

        function.setImportName(importAnnot.getValue("name").getString());
        var moduleName = importAnnot.getValue("module");
        function.setImportModule(moduleName != null ? moduleName.getString() : "teavm");
    }

    private void addInitializerErase(MethodReader method, WasmFunction function) {
        if (method.hasModifier(ElementModifier.STATIC) && method.getName().equals("<clinit>")
                && method.parameterCount() == 0 && classInitInfo.isDynamicInitializer(method.getOwnerName())) {
            var classInfo = classInfoProvider.getClassInfo(method.getOwnerName());
            var erase = new WasmSetGlobal(classInfo.getInitializerPointer(),
                    new WasmFunctionReference(getDummyInitializer()));
            function.getBody().add(erase);
        }
    }

    private Decompiler getDecompiler() {
        if (decompiler == null) {
            decompiler = new Decompiler(classes, Set.of(), friendlyToDebugger);
        }
        return decompiler;
    }

    private WasmGCGenerationContext getGenerationContext() {
        if (context == null) {
            context = new WasmGCGenerationContext(
                    module,
                    virtualTables,
                    typeMapper,
                    functionTypes,
                    classes,
                    this,
                    supertypeFunctions,
                    classInfoProvider,
                    standardClasses,
                    strings,
                    customGenerators
            );
        }
        return context;
    }

    public WasmFunction getDummyInitializer() {
        if (dummyInitializer == null) {
            dummyInitializer = new WasmFunction(functionTypes.of(null));
            dummyInitializer.getBody().add(new WasmReturn());
            dummyInitializer.setName("teavm_dummy_initializer");
            dummyInitializer.setReferenced(true);
            module.functions.add(dummyInitializer);
        }
        return dummyInitializer;
    }

    private WasmGCCustomGeneratorContext customGeneratorContext = new WasmGCCustomGeneratorContext() {
        @Override
        public WasmModule module() {
            return module;
        }

        @Override
        public WasmFunctionTypes functionTypes() {
            return functionTypes;
        }
    };
}
