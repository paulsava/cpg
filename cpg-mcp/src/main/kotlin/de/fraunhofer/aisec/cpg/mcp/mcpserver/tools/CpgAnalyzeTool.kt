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
@file:Suppress("UNCHECKED_CAST")

package de.fraunhofer.aisec.cpg.mcp.mcpserver.tools

import de.fraunhofer.aisec.cpg.*
import de.fraunhofer.aisec.cpg.graph.Component
import de.fraunhofer.aisec.cpg.graph.EOGStarterHolder
import de.fraunhofer.aisec.cpg.graph.Node
import de.fraunhofer.aisec.cpg.graph.OverlayNode
import de.fraunhofer.aisec.cpg.graph.allChildrenWithOverlays
import de.fraunhofer.aisec.cpg.graph.calls
import de.fraunhofer.aisec.cpg.graph.declarations.TranslationUnitDeclaration
import de.fraunhofer.aisec.cpg.graph.firstParentOrNull
import de.fraunhofer.aisec.cpg.graph.functions
import de.fraunhofer.aisec.cpg.graph.nodes
import de.fraunhofer.aisec.cpg.graph.records
import de.fraunhofer.aisec.cpg.graph.variables
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.CpgAnalysisResult
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.CpgAnalyzePayload
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.CpgRunPassPayload
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.StatusInfo
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.runOnCpg
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.toObject
import de.fraunhofer.aisec.cpg.mcp.setupTranslationConfiguration
import de.fraunhofer.aisec.cpg.passes.ComponentPass
import de.fraunhofer.aisec.cpg.passes.EOGStarterPass
import de.fraunhofer.aisec.cpg.passes.Pass
import de.fraunhofer.aisec.cpg.passes.TranslationResultPass
import de.fraunhofer.aisec.cpg.passes.TranslationUnitPass
import de.fraunhofer.aisec.cpg.passes.configuration.PassOrderingHelper
import de.fraunhofer.aisec.cpg.passes.configuration.ReplacePass
import de.fraunhofer.aisec.cpg.passes.consumeTargets
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.io.File
import java.nio.file.Files
import java.util.IdentityHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.typeOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

var globalAnalysisResult: TranslationResult? = null

var ctx: TranslationContext? = null

/** Keeps track of which passes have been run on which nodes to avoid redundant executions. */
val nodeToPass = IdentityHashMap<Node, MutableSet<KClass<out Pass<*>>>>()

fun Server.addAnalyzeCode() {
    val description =
        "Parse source code and build a Code Property Graph. Returns a summary of the analysis."
    val inputSchema =
        ToolSchema(
            properties =
                buildJsonObject {
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "Source code to analyze")
                    }
                    putJsonObject("extension") {
                        put("type", "string")
                        put("description", "File extension, e.g. 'py', 'java', 'cpp'")
                    }
                    putJsonObject("runPasses") {
                        put("type", "boolean")
                        put("description", "Run all analysis passes (default: true)")
                    }
                },
            required = listOf("content", "extension"),
        )

    this.addTool(name = "analyze_code", description = description, inputSchema = inputSchema) {
        request ->
        try {
            val payload =
                request.arguments?.toObject<CpgAnalyzePayload>()
                    ?: return@addTool CallToolResult(
                        content =
                            listOf(TextContent("Invalid or missing payload for analyze_code tool."))
                    )
            val runPasses =
                request.arguments?.get("runPasses")?.jsonPrimitive?.booleanOrNull ?: true
            val analysisResult = runCpgAnalyze(payload, runPasses = runPasses, cleanup = true)
            val jsonResult = Json.encodeToString(analysisResult)
            CallToolResult(content = listOf(TextContent(jsonResult)))
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error: ${e.message ?: e::class.simpleName}"))
            )
        }
    }
}

fun Server.addGetStatus() {
    this.addTool(
        name = "get_status",
        description = "Show current analysis state: what's loaded, passes run, overlays applied.",
    ) { _ ->
        val result = globalAnalysisResult
        if (result == null) {
            val statusInfo =
                StatusInfo(
                    hasAnalysis = false,
                    totalNodes = 0,
                    functions = 0,
                    records = 0,
                    calls = 0,
                    variables = 0,
                    overlaysApplied = 0,
                    passesRun = emptyList(),
                )
            return@addTool CallToolResult(
                content = listOf(TextContent(Json.encodeToString(statusInfo)))
            )
        }

        val passesRun =
            nodeToPass.values
                .flatten()
                .mapNotNull { it.qualifiedName ?: it.simpleName }
                .distinct()
                .sorted()

        val statusInfo =
            StatusInfo(
                hasAnalysis = true,
                totalNodes = result.nodes.size,
                functions = result.functions.size,
                records = result.records.size,
                calls = result.calls.size,
                variables = result.variables.size,
                overlaysApplied = result.allChildrenWithOverlays<OverlayNode>().size,
                passesRun = passesRun,
            )

        CallToolResult(content = listOf(TextContent(Json.encodeToString(statusInfo))))
    }
}

/**
 * Translate the given [payload] to the CPG. If there has been another analysis before, it resets
 * the context and cleans up all frontends.
 *
 * If [runPasses] is true, all default passes will be run, otherwise no pass will be run. If
 * [cleanup] is true, we clean up the [TypeManager] memory after analysis.
 */
fun runCpgAnalyze(
    payload: CpgAnalyzePayload?,
    runPasses: Boolean,
    cleanup: Boolean,
): CpgAnalysisResult {
    val (file, topLevel) =
        when {
            payload?.content != null -> {
                val extension =
                    if (payload.extension != null) {
                        if (payload.extension.startsWith(".")) payload.extension
                        else ".${payload.extension}"
                    } else {
                        throw IllegalArgumentException(
                            "Extension is required when providing content"
                        )
                    }

                if (extension == ".java") {
                    val tempDir = Files.createTempDirectory("cpg_analysis").toFile()
                    val tempFile = File(tempDir, "CpgAnalysis$extension")
                    tempFile.writeText(payload.content)
                    tempFile.deleteOnExit()
                    tempDir.deleteOnExit()
                    tempFile to tempDir
                } else {
                    val tempFile = File.createTempFile("cpg_analysis", extension)
                    tempFile.writeText(payload.content)
                    tempFile.deleteOnExit()
                    tempFile to tempFile
                }
            }

            else -> throw IllegalArgumentException("Must provide content")
        }

    val config =
        setupTranslationConfiguration(
            topLevel = topLevel,
            files = listOf(file.absolutePath),
            includePaths = emptyList(),
            runPasses = runPasses,
        )
    config.disableCleanup = !cleanup

    if (ctx != null) {
        ctx?.executedFrontends?.forEach { frontend ->
            // If there has been another analysis before, reset the context and clean up all
            // frontends.
            frontend.cleanup()
        }

        ctx = null
    }

    val analyzer = TranslationManager.builder().config(config).build()
    ctx = TranslationContext(config)
    val result =
        ctx?.let { ctx -> analyzer.analyze(ctx).get() }
            ?: throw IllegalStateException("Translation context is not initialized")

    // Store the result globally
    globalAnalysisResult = result
    nodeToPass.clear()

    val topLevelDeclarations =
        result.components
            .flatMap { it.translationUnits }
            .flatMap { it.declarations }
            .map { it.name.toString() }
            .filter { it.isNotBlank() }
            .distinct()

    return CpgAnalysisResult(
        totalNodes = result.nodes.size,
        functions = result.functions.size,
        variables = result.variables.size,
        callExpressions = result.calls.size,
        records = result.records.size,
        topLevelDeclarations = topLevelDeclarations,
    )
}

/**
 * Registers a tool which runs a [Pass] on a specified [Node] or the closest suitable node(s) for
 * the pass by first searching upwards and then (in case no suitable node was found) downwards the
 * AST. The tool further takes care of dependencies between the passes.
 */
fun Server.addRunPass() {
    this.addTool(
        name = "run_pass",
        description =
            "Runs a given pass on a specified node, resolving pass dependencies automatically.",
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("passName") {
                            put("type", "string")
                            put("description", "The FQN of the pass to run")
                        }
                        putJsonObject("nodeId") {
                            put("type", "string")
                            put("description", "The ID of the node on which the pass should run")
                        }
                    },
                required = listOf("passName", "nodeId"),
            ),
    ) { request ->
        request.runOnCpg { result: TranslationResult, request: CallToolRequest ->
            val payload =
                request.arguments?.toObject<CpgRunPassPayload>()
                    ?: return@runOnCpg CallToolResult(
                        content =
                            listOf(TextContent("Invalid or missing payload for run_pass tool."))
                    )
            val passClass =
                try {
                    (Class.forName(payload.passName).kotlin as? KClass<out Pass<*>>)
                        ?: return@runOnCpg CallToolResult(
                            content =
                                listOf(TextContent("Could not find the pass ${payload.passName}."))
                        )
                } catch (_: ClassNotFoundException) {
                    return@runOnCpg CallToolResult(
                        content =
                            listOf(TextContent("Could not find the pass ${payload.passName}."))
                    )
                }

            val nodes = result.nodes.filter { it.id.toString() == payload.nodeId }

            if (nodes.isEmpty())
                return@runOnCpg CallToolResult(
                    content =
                        listOf(
                            TextContent("Could not find any node with the ID ${payload.nodeId}.")
                        )
                )
            val executedPasses = mutableListOf<TextContent>()
            // Check if all required passes have been run before executing this pass.
            val orderingHelper = PassOrderingHelper(listOf(passClass))
            val orderedPassesToExecute =
                try {
                    orderingHelper.order().flatten()
                } catch (_: ConfigurationException) {
                    // There was an exception while ordering the passes (e.g., cyclic dependency).
                    // We just add the requested pass and hope that the user knows what they are
                    // doing.
                    listOf(passClass)
                }

            for (node in nodes) {
                for (passToExecute in orderedPassesToExecute) {
                    // Check if pass has already been executed for the respective node
                    if (passToExecute !in nodeToPass.computeIfAbsent(node) { mutableSetOf() }) {
                        // Execute the pass for the node
                        ctx?.let { ctx ->
                            val passResult = runPassForNode(node, passToExecute, ctx)
                            if (passResult.success) {
                                executedPasses.add(TextContent(passResult.message))
                            } else {
                                // Return if there was an error during pass execution
                                return@runOnCpg CallToolResult(
                                    content =
                                        listOf(
                                            TextContent(passResult.message),
                                            *executedPasses.toTypedArray(),
                                        )
                                )
                            }
                            // Mark pass as executed
                            nodeToPass[node]?.add(passToExecute)
                        }
                            ?: return@runOnCpg CallToolResult(
                                content =
                                    listOf(
                                        TextContent(
                                            "Cannot run run_pass without translation context. Run analyze_code first."
                                        )
                                    )
                            )
                    }
                }
            }

            CallToolResult(
                content =
                    listOf(
                        TextContent(
                            "Successfully ran ${payload.passName} on node ${payload.nodeId}."
                        ),
                        *executedPasses.toTypedArray(),
                    )
            )
        }
    }
}

data class PassExecutionResult(val success: Boolean, val message: String)

/**
 * Internal helper function that runs the [Pass] of class [passClass] on the given [node] within the
 * provided [TranslationContext] [ctx]. As a [Pass] has to work on one or multiple nodes, one can
 * provide the list of nodes where the pass should start using the [preList]. If [preList] is not
 * provided, it collects nodes of type [T] starting from the given [node] (either the node itself,
 * its first parent of type [T], or all children of type [T]).
 */
inline fun <reified T : Node> runPassForNode(
    node: Node,
    passClass: KClass<out Pass<T>>,
    ctx: TranslationContext,
    preList: List<T>? = null,
): PassExecutionResult {
    val nodesToAnalyze = preList?.toMutableList() ?: listOfNotNull(node as? T).toMutableList()
    if (nodesToAnalyze.isEmpty())
        nodesToAnalyze.addAll(
            node.firstParentOrNull<T>()?.let { listOf(it) } ?: node.allChildrenWithOverlays<T>()
        )
    return if (nodesToAnalyze.isNotEmpty()) {
        val messages = mutableListOf<String>()
        for ((language, nodes) in nodesToAnalyze.groupBy { it.language }) {
            // Check if we have to replace the pass for this language
            val actualClass =
                (language::class.findAnnotations<ReplacePass>().find { it.old == passClass }?.with
                    as? KClass<out Pass<T>>) ?: passClass

            consumeTargets(
                cls = actualClass,
                ctx = ctx,
                targets =
                    nodes.filter {
                        nodeToPass.computeIfAbsent(it) { mutableSetOf() }.add(actualClass)
                    },
                executedFrontends = ctx.executedFrontends,
            )
            messages +=
                "Ran pass ${actualClass.simpleName} on nodes ${nodesToAnalyze.map { it.id.toString() }}."
        }
        PassExecutionResult(true, messages.joinToString(","))
    } else {
        PassExecutionResult(
            false,
            "Expected node of type ${typeOf<T>()} for pass ${passClass.simpleName}, but got ${node.javaClass.simpleName}",
        )
    }
}

/**
 * Runs the specified [passClass] on the given [node] within the provided [ctx]
 * (TranslationContext). To determine the appropriate targets for the pass, it checks the type of
 * the pass and collects nodes accordingly:
 * - For [TranslationResultPass], it looks for the nearest [TranslationResult] parent or the node
 *   itself if it is a [TranslationResult].
 * - For [ComponentPass], it checks if the node is a [Component] or searches for the nearest
 *   [Component] parent or all children that are [Component]s.
 * - For [TranslationUnitPass], it checks if the node is a [TranslationUnitDeclaration] or searches
 *   for the nearest [TranslationUnitDeclaration] parent or all children that are
 *   [TranslationUnitDeclaration]s.
 * - For [EOGStarterPass], it checks if the node is an [EOGStarterHolder] or searches for the
 *   nearest [EOGStarterHolder] parent with no previous EOG or all children that are
 *   [EOGStarterHolder]s with no previous EOG.
 *
 * Returns a [CallToolResult] if there was an error during execution, otherwise returns null.
 */
fun runPassForNode(
    node: Node,
    passClass: KClass<out Pass<*>>,
    ctx: TranslationContext,
): PassExecutionResult {
    val prototype =
        passClass.primaryConstructor?.call(ctx)
            ?: return PassExecutionResult(
                false,
                "Could not create the pass ${passClass.simpleName}.",
            )

    return when (prototype) {
        is TranslationResultPass -> {
            runPassForNode<TranslationResult>(node, prototype::class, ctx)
        }
        is ComponentPass -> {
            runPassForNode<Component>(node, prototype::class, ctx)
        }
        is TranslationUnitPass -> {
            runPassForNode<TranslationUnitDeclaration>(node, prototype::class, ctx)
        }
        is EOGStarterPass -> {
            val eogStarters = listOfNotNull((node as? EOGStarterHolder) as? Node).toMutableList()
            if (eogStarters.isEmpty())
                eogStarters.addAll(
                    node
                        .firstParentOrNull<Node> { it is EOGStarterHolder && it.prevEOG.isEmpty() }
                        ?.let { listOf(it) }
                        ?: node.allChildrenWithOverlays<Node> {
                            it is EOGStarterHolder && it.prevEOG.isEmpty()
                        }
                )
            runPassForNode<Node>(node, prototype::class, ctx, preList = eogStarters)
        }
    }
}
