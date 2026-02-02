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
package de.fraunhofer.aisec.cpg.mcp.mcpserver.tools

import de.fraunhofer.aisec.cpg.TranslationResult
import de.fraunhofer.aisec.cpg.graph.calls
import de.fraunhofer.aisec.cpg.graph.concepts.Concept
import de.fraunhofer.aisec.cpg.graph.concepts.conceptBuildHelper
import de.fraunhofer.aisec.cpg.graph.concepts.operationBuildHelper
import de.fraunhofer.aisec.cpg.graph.functions
import de.fraunhofer.aisec.cpg.graph.nodes
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.ApplyOverlayPayload
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.CallInfo
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.FunctionInfo
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.SuggestOverlaysPayload
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.getAvailableConcepts
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.getAvailableOperations
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.runOnCpg
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.toObject
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun Server.addApplyOverlay() {
    val description =
        """Apply a single concept or operation overlay to a CPG node.
Concepts mark 'what something IS' (applied to data nodes).
Operations mark 'what something DOES' (applied to call/action nodes, must reference a concept).
Use suggest_overlays first to get recommendations."""

    val inputSchema =
        ToolSchema(
            properties =
                buildJsonObject {
                    putJsonObject("nodeId") {
                        put("type", "string")
                        put("description", "ID of the node to apply the overlay to")
                    }
                    putJsonObject("overlayFqn") {
                        put("type", "string")
                        put("description", "Fully qualified class name of the concept or operation")
                    }
                    putJsonObject("overlayType") {
                        put("type", "string")
                        put("description", "'Concept' or 'Operation'")
                    }
                    putJsonObject("conceptNodeId") {
                        put("type", "string")
                        put(
                            "description",
                            "For Operations only: ID of the node that has the parent Concept applied",
                        )
                    }
                },
            required = listOf("nodeId", "overlayFqn", "overlayType"),
        )

    this.addTool(name = "apply_overlay", description = description, inputSchema = inputSchema) {
        request ->
        request.runOnCpg { result: TranslationResult, request: CallToolRequest ->
            val payload =
                request.arguments?.toObject<ApplyOverlayPayload>()
                    ?: return@runOnCpg CallToolResult(
                        content =
                            listOf(TextContent("Invalid or missing payload for apply_overlay."))
                    )

            val node =
                result.nodes.find { it.id.toString() == payload.nodeId }
                    ?: return@runOnCpg CallToolResult(
                        content = listOf(TextContent("Node not found: ${payload.nodeId}"))
                    )

            try {
                when (payload.overlayType.lowercase()) {
                    "concept" -> {
                        result.conceptBuildHelper(
                            name = payload.overlayFqn,
                            underlyingNode = node,
                            constructorArguments = emptyMap(),
                            connectDFGUnderlyingNodeToConcept = true,
                        )
                        CallToolResult(
                            content =
                                listOf(
                                    TextContent(
                                        "Applied concept ${payload.overlayFqn} to node ${payload.nodeId} (${node::class.simpleName})"
                                    )
                                )
                        )
                    }
                    "operation" -> {
                        val conceptNodeId =
                            payload.conceptNodeId
                                ?: return@runOnCpg CallToolResult(
                                    content =
                                        listOf(
                                            TextContent(
                                                "conceptNodeId is required when applying operations."
                                            )
                                        )
                                )
                        val conceptNode = result.nodes.find { it.id.toString() == conceptNodeId }
                        val concept =
                            conceptNode?.overlays?.filterIsInstance<Concept>()?.firstOrNull()
                                ?: return@runOnCpg CallToolResult(
                                    content =
                                        listOf(
                                            TextContent(
                                                "No concept found on node ${conceptNodeId}. Apply a concept first."
                                            )
                                        )
                                )

                        result.operationBuildHelper(
                            name = payload.overlayFqn,
                            underlyingNode = node,
                            concept = concept,
                            constructorArguments = emptyMap(),
                            connectDFGUnderlyingNodeToConcept = true,
                        )
                        CallToolResult(
                            content =
                                listOf(
                                    TextContent(
                                        "Applied operation ${payload.overlayFqn} to node ${payload.nodeId} with concept ${concept::class.simpleName}"
                                    )
                                )
                        )
                    }
                    else ->
                        CallToolResult(
                            content =
                                listOf(
                                    TextContent(
                                        "Unknown overlay type '${payload.overlayType}'. Use 'Concept' or 'Operation'."
                                    )
                                )
                        )
                }
            } catch (e: Exception) {
                CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                "Failed to apply overlay ${payload.overlayFqn} to node ${payload.nodeId}: ${e.message}"
                            )
                        )
                )
            }
        }
    }
}

fun Server.addSuggestOverlays() {
    val description =
        """Get suggestions for which concepts and operations to apply to the current graph.
Returns: available overlay types and a summary of nodes that are good candidates for tagging.
Use after analyze_code. Apply suggestions with apply_overlay."""

    this.addTool(
        name = "suggest_overlays",
        description = description,
        inputSchema =
            ToolSchema(
                properties =
                    buildJsonObject {
                        putJsonObject("description") {
                            put("type", "string")
                            put("description", "Optional focus area")
                        }
                    },
                required = listOf(),
            ),
    ) { request ->
        request.runOnCpg { result: TranslationResult, request: CallToolRequest ->
            request.arguments?.toObject<SuggestOverlaysPayload>()
            val concepts = getAvailableConcepts().map { it.name }
            val operations = getAvailableOperations().map { it.name }
            val candidateFunctions = result.functions.take(20).map { FunctionInfo(it) }
            val candidateCalls = result.calls.take(30).map { CallInfo(it) }

            val response = buildJsonObject {
                put(
                    "availableConcepts",
                    Json.encodeToJsonElement(ListSerializer(String.serializer()), concepts),
                )
                put(
                    "availableOperations",
                    Json.encodeToJsonElement(ListSerializer(String.serializer()), operations),
                )
                put(
                    "candidateFunctions",
                    Json.encodeToJsonElement(
                        ListSerializer(FunctionInfo.serializer()),
                        candidateFunctions,
                    ),
                )
                put(
                    "candidateCalls",
                    Json.encodeToJsonElement(ListSerializer(CallInfo.serializer()), candidateCalls),
                )
            }

            CallToolResult(content = listOf(TextContent(response.toString())))
        }
    }
}
