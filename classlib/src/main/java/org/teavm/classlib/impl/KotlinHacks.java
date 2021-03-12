/*
 *  Copyright 2019 Alexey Andreev.
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
package org.teavm.classlib.impl;

import java.util.ArrayList;
import java.util.List;
import org.teavm.model.AccessLevel;
import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.MethodHolder;
import org.teavm.model.Program;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.StringConstantInstruction;

public class KotlinHacks implements ClassHolderTransformer {
    @Override
    public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (cls.getName().equals("kotlin.jvm.internal.Lambda")
                || cls.getName().equals("kotlin.coroutines.jvm.internal.BaseContinuationImpl")) {
            List<MethodHolder> methodsToRemove = new ArrayList<>();
            for (MethodHolder method : cls.getMethods()) {
                if (method.getName().equals("toString")) {
                    methodsToRemove.add(method);
                }
            }
            for (MethodHolder method : methodsToRemove) {
                cls.removeMethod(method);
            }
        } else if (cls.getName().equals("kotlin.jvm.internal.Reflection")) {
            for (MethodHolder method : cls.getMethods()) {
                if (method.getLevel() == AccessLevel.PUBLIC && method.hasModifier(ElementModifier.STATIC)
                        && method.getName().equals("renderLambdaToString")
                        && method.getResultType().isObject(String.class)) {
                    Program program = new Program();
                    BasicBlock block = program.createBasicBlock();

                    StringConstantInstruction stringConstant = new StringConstantInstruction();
                    stringConstant.setReceiver(program.createVariable());
                    stringConstant.setConstant("lambda");
                    block.add(stringConstant);

                    ExitInstruction exit = new ExitInstruction();
                    exit.setValueToReturn(stringConstant.getReceiver());
                    block.add(exit);

                    method.setProgram(program);
                }
            }
        }
    }
}
