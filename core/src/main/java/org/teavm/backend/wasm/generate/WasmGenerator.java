/*
 *  Copyright 2016 Alexey Andreev.
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
package org.teavm.backend.wasm.generate;

import java.util.function.Predicate;
import org.teavm.ast.RegularMethodNode;
import org.teavm.ast.VariableNode;
import org.teavm.ast.decompilation.Decompiler;
import org.teavm.backend.lowlevel.generate.NameProvider;
import org.teavm.backend.wasm.binary.BinaryWriter;
import org.teavm.backend.wasm.debug.info.VariableType;
import org.teavm.backend.wasm.model.WasmFunction;
import org.teavm.backend.wasm.model.WasmLocal;
import org.teavm.backend.wasm.model.WasmTag;
import org.teavm.backend.wasm.model.WasmType;
import org.teavm.interop.Export;
import org.teavm.interop.Import;
import org.teavm.model.AnnotationReader;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;

public class WasmGenerator {
    private Decompiler decompiler;
    private ClassHolderSource classSource;
    private WasmGenerationContext context;
    private WasmClassGenerator classGenerator;
    private BinaryWriter binaryWriter;
    private NameProvider names;
    private Predicate<MethodReference> asyncMethods;
    private WasmTag exceptionTag;

    public WasmGenerator(Decompiler decompiler, ClassHolderSource classSource,
            WasmGenerationContext context, WasmClassGenerator classGenerator, BinaryWriter binaryWriter,
            Predicate<MethodReference> asyncMethods) {
        this.decompiler = decompiler;
        this.classSource = classSource;
        this.context = context;
        this.classGenerator = classGenerator;
        this.binaryWriter = binaryWriter;
        names = classGenerator.names;
        this.asyncMethods = asyncMethods;
    }

    public WasmFunction generateDefinition(MethodReference methodReference) {
        ClassHolder cls = classSource.get(methodReference.getClassName());
        MethodHolder method = cls.getMethod(methodReference.getDescriptor());
        WasmFunction function = new WasmFunction(names.forMethod(method.getReference()));
        function.setJavaMethod(methodReference);

        MethodReader reader = classSource.resolve(methodReference);

        AnnotationReader exportAnnot = reader.getAnnotations().get(Export.class.getName());
        if (exportAnnot != null) {
            function.setExportName(exportAnnot.getValue("name").getString());
        }

        AnnotationReader importAnnot = reader.getAnnotations().get(Import.class.getName());
        if (importAnnot != null) {
            function.setImportName(importAnnot.getValue("name").getString());
        }

        if (!method.hasModifier(ElementModifier.STATIC)) {
            function.getParameters().add(WasmType.INT32);
        }
        for (int i = 0; i < method.parameterCount(); ++i) {
            function.getParameters().add(WasmGeneratorUtil.mapType(method.parameterType(i)));
        }
        if (method.getResultType() != ValueType.VOID) {
            function.setResult(WasmGeneratorUtil.mapType(method.getResultType()));
        }

        return function;
    }

    public WasmFunction generate(MethodReference methodReference, MethodHolder bodyMethod) {
        ClassHolder cls = classSource.get(methodReference.getClassName());
        MethodHolder method = cls.getMethod(methodReference.getDescriptor());

        RegularMethodNode methodAst = decompiler.decompileRegular(bodyMethod);
        WasmFunction function = context.getFunction(names.forMethod(methodReference));
        int firstVariable = method.hasModifier(ElementModifier.STATIC) ? 1 : 0;
        for (int i = firstVariable; i < methodAst.getVariables().size(); ++i) {
            VariableNode variable = methodAst.getVariables().get(i);
            WasmType type = variable.getType() != null
                    ? WasmGeneratorUtil.mapType(variable.getType())
                    : WasmType.INT32;
            var local = new WasmLocal(type, variable.getName());
            local.setJavaType(mapType(variable.getType()));
            function.add(local);
        }

        var visitor = new WasmGenerationVisitor(context, classGenerator, binaryWriter, function, methodReference,
                firstVariable, asyncMethods.test(methodReference));
        visitor.generate(methodAst.getBody(), function.getBody());

        AnnotationReader exportAnnot = method.getAnnotations().get(Export.class.getName());
        if (exportAnnot != null) {
            function.setExportName(exportAnnot.getValue("name").getString());
        }

        return function;
    }

    private VariableType mapType(org.teavm.model.util.VariableType type) {
        switch (type) {
            case INT:
                return VariableType.INT;
            case LONG:
                return VariableType.LONG;
            case FLOAT:
                return VariableType.FLOAT;
            case DOUBLE:
                return VariableType.DOUBLE;
            default:
                return VariableType.OBJECT;
        }
    }

    public WasmFunction generateNative(MethodReference methodReference) {
        WasmFunction function = context.getFunction(names.forMethod(methodReference));

        WasmGenerationContext.ImportedMethod importedMethod = context.getImportedMethod(methodReference);
        if (importedMethod != null) {
            function.setImportName(importedMethod.name);
            function.setImportModule(importedMethod.module);
        } else {
            function.setImportName("<unknown>");
        }

        return function;
    }
}
