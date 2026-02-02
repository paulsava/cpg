/*
 * Copyright (c) 2026, Fraunhofer AISEC. All rights reserved.
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
 *
 *                    $$$$$$\  $$$$$$$\   $$$$$$\
 *                   $$  __$$\ $$  __$$\ $$  __$$\
 *                   $$ /  \__|$$ |  $$ |$$ /  \__|
 *                   $$ |      $$$$$$$  |$$ |$$$$\
 *                   $$ |      $$  ____/ $$ |\_$$ |
 *                   $$ |  $$\ $$ |      $$ |  $$ |
 *                   \$$$$$   |$$ |      \$$$$$   |
 *                    \______/ \__|       \______/
 *
 */
package de.fraunhofer.aisec.cpg.mcp.mcpserver.resources

import de.fraunhofer.aisec.cpg.TranslationResult
import de.fraunhofer.aisec.cpg.graph.Component
import de.fraunhofer.aisec.cpg.graph.EOGStarterHolder
import de.fraunhofer.aisec.cpg.graph.declarations.TranslationUnitDeclaration
import de.fraunhofer.aisec.cpg.mcp.mcpserver.cpgDescription
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.PassInfo
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.getAvailableConcepts
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.getAvailableOperations
import de.fraunhofer.aisec.cpg.passes.BasicBlockCollectorPass
import de.fraunhofer.aisec.cpg.passes.ComponentPass
import de.fraunhofer.aisec.cpg.passes.ControlDependenceGraphPass
import de.fraunhofer.aisec.cpg.passes.ControlFlowSensitiveDFGPass
import de.fraunhofer.aisec.cpg.passes.DFGPass
import de.fraunhofer.aisec.cpg.passes.DynamicInvokeResolver
import de.fraunhofer.aisec.cpg.passes.EOGStarterPass
import de.fraunhofer.aisec.cpg.passes.EvaluationOrderGraphPass
import de.fraunhofer.aisec.cpg.passes.ImportResolver
import de.fraunhofer.aisec.cpg.passes.Pass
import de.fraunhofer.aisec.cpg.passes.PrepareSerialization
import de.fraunhofer.aisec.cpg.passes.ProgramDependenceGraphPass
import de.fraunhofer.aisec.cpg.passes.ResolveCallExpressionAmbiguityPass
import de.fraunhofer.aisec.cpg.passes.ResolveMemberExpressionAmbiguityPass
import de.fraunhofer.aisec.cpg.passes.SccPass
import de.fraunhofer.aisec.cpg.passes.SymbolResolver
import de.fraunhofer.aisec.cpg.passes.TranslationResultPass
import de.fraunhofer.aisec.cpg.passes.TranslationUnitPass
import de.fraunhofer.aisec.cpg.passes.TypeHierarchyResolver
import de.fraunhofer.aisec.cpg.passes.TypeResolver
import de.fraunhofer.aisec.cpg.passes.briefDescription
import de.fraunhofer.aisec.cpg.passes.hardDependencies
import de.fraunhofer.aisec.cpg.passes.softDependencies
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlin.reflect.KClass
import kotlinx.serialization.json.Json

fun Server.addCpgResources() {
    addResource(
        uri = "cpg://docs/graph-model",
        name = "CPG Graph Model",
        description =
            "Documentation of the Code Property Graph structure: AST, DFG, EOG, CDG, PDG edges and node types",
        mimeType = "text/plain",
    ) { request ->
        ReadResourceResult(
            contents =
                listOf(TextResourceContents(cpgDescription.trimIndent(), request.uri, "text/plain"))
        )
    }

    val conceptNames = getAvailableConcepts().map { it.name }
    addResource(
        uri = "cpg://docs/available-concepts",
        name = "Available Concepts",
        description = "List of all concept classes that can be applied to CPG nodes",
        mimeType = "text/plain",
    ) { request ->
        ReadResourceResult(
            contents =
                listOf(
                    TextResourceContents(conceptNames.joinToString("\n"), request.uri, "text/plain")
                )
        )
    }

    val operationNames = getAvailableOperations().map { it.name }
    addResource(
        uri = "cpg://docs/available-operations",
        name = "Available Operations",
        description = "List of all operation classes that can be applied to CPG nodes",
        mimeType = "text/plain",
    ) { request ->
        ReadResourceResult(
            contents =
                listOf(
                    TextResourceContents(
                        operationNames.joinToString("\n"),
                        request.uri,
                        "text/plain",
                    )
                )
        )
    }

    val passesJson = buildPassCatalogJson()
    addResource(
        uri = "cpg://docs/passes",
        name = "CPG Passes",
        description =
            "Catalog of all available analysis passes with their dependencies and required node types",
        mimeType = "application/json",
    ) { request ->
        ReadResourceResult(
            contents = listOf(TextResourceContents(passesJson, request.uri, "application/json"))
        )
    }
}

fun passToInfo(pass: KClass<out Pass<*>>): PassInfo {
    return PassInfo(
        fqn = pass.qualifiedName.toString(),
        description = pass.briefDescription,
        requiredNodeType =
            pass.supertypes.fold("") { old, it ->
                old +
                    when (it.classifier) {
                        EOGStarterPass::class -> EOGStarterHolder::class.qualifiedName.toString()
                        TranslationUnitPass::class ->
                            TranslationUnitDeclaration::class.qualifiedName.toString()
                        TranslationResultPass::class ->
                            TranslationResult::class.qualifiedName.toString()
                        ComponentPass::class -> Component::class.qualifiedName.toString()
                        else -> ""
                    }
            },
        dependsOn = pass.hardDependencies.map { it.qualifiedName.toString() },
        softDependencies = pass.softDependencies.map { it.qualifiedName.toString() },
    )
}

fun optionalPassToInfo(passName: String): PassInfo? {
    return try {
        @Suppress("UNCHECKED_CAST")
        (Class.forName(passName).kotlin as? KClass<out Pass<*>>)?.let { passToInfo(it) }
    } catch (_: ClassNotFoundException) {
        null
    }
}

fun buildPassCatalogJson(): String {
    val passesList =
        mutableListOf(
            passToInfo(PrepareSerialization::class),
            passToInfo(DynamicInvokeResolver::class),
            passToInfo(ImportResolver::class),
            passToInfo(SymbolResolver::class),
            passToInfo(EvaluationOrderGraphPass::class),
            passToInfo(DFGPass::class),
            passToInfo(ControlFlowSensitiveDFGPass::class),
            passToInfo(ControlDependenceGraphPass::class),
            passToInfo(ProgramDependenceGraphPass::class),
            passToInfo(TypeResolver::class),
            passToInfo(TypeHierarchyResolver::class),
            passToInfo(ResolveMemberExpressionAmbiguityPass::class),
            passToInfo(ResolveCallExpressionAmbiguityPass::class),
            passToInfo(SccPass::class),
            passToInfo(BasicBlockCollectorPass::class),
        )

    optionalPassToInfo("de.fraunhofer.aisec.cpg.passes.concepts.file.python.PythonFileConceptPass")
        ?.let { passesList += it }
    optionalPassToInfo("de.fraunhofer.aisec.cpg.passes.PythonAddDeclarationsPass")?.let {
        passesList += it
    }
    optionalPassToInfo("de.fraunhofer.aisec.cpg.passes.CXXExtraPass")?.let { passesList += it }
    optionalPassToInfo("de.fraunhofer.aisec.cpg.passes.CompressLLVMPass")?.let { passesList += it }
    optionalPassToInfo("de.fraunhofer.aisec.cpg.passes.JavaExternalTypeHierarchyResolver")?.let {
        passesList += it
    }
    optionalPassToInfo("de.fraunhofer.aisec.cpg.passes.JavaExtraPass")?.let { passesList += it }
    optionalPassToInfo("de.fraunhofer.aisec.cpg.passes.JavaImportResolver")?.let {
        passesList += it
    }
    optionalPassToInfo("de.fraunhofer.aisec.cpg.passes.GoExtraPass")?.let { passesList += it }
    optionalPassToInfo("de.fraunhofer.aisec.cpg.passes.GoEvaluationOrderGraphPass")?.let {
        passesList += it
    }

    return Json.encodeToString(passesList)
}
