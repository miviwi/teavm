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
package org.teavm.backend.wasm.model;

import org.teavm.common.Graph;
import org.teavm.common.GraphBuilder;

final class WasmTypeGraphBuilder {
    private WasmTypeGraphBuilder() {
    }

    static Graph buildTypeGraph(Iterable<WasmCompositeType> types, int size) {
        var graphBuilder = new GraphBuilder(size);
        var visitor = new GraphBuilderVisitor(graphBuilder);
        for (var type : types) {
            visitor.currentIndex = type.index;
            type.acceptVisitor(visitor);
        }
        return graphBuilder.build();
    }

    private static class GraphBuilderVisitor implements WasmCompositeTypeVisitor {
        final GraphBuilder graphBuilder;
        int currentIndex;

        GraphBuilderVisitor(GraphBuilder graphBuilder) {
            this.graphBuilder = graphBuilder;
        }

        @Override
        public void visit(WasmStructure type) {
            for (var field : type.getFields()) {
                addEdge(field.asUnpackedType());
            }
        }

        @Override
        public void visit(WasmArray type) {
            addEdge(type.getElementType().asUnpackedType());
        }

        @Override
        public void visit(WasmFunctionType type) {
            for (var parameter : type.getParameterTypes()) {
                addEdge(parameter);
            }
            if (type.getReturnType() != null) {
                addEdge(type.getReturnType());
            }
        }

        private void addEdge(WasmType type) {
            if (type instanceof WasmType.CompositeReference) {
                var composite = ((WasmType.CompositeReference) type).composite;
                graphBuilder.addEdge(currentIndex, composite.index);
            }
        }
    }
}
