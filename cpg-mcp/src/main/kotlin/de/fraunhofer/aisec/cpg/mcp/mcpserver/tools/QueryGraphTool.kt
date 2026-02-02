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
import de.fraunhofer.aisec.cpg.graph.OverlayNode
import de.fraunhofer.aisec.cpg.graph.allChildrenWithOverlays
import de.fraunhofer.aisec.cpg.graph.calls
import de.fraunhofer.aisec.cpg.graph.callsByName
import de.fraunhofer.aisec.cpg.graph.declarations.FunctionDeclaration
import de.fraunhofer.aisec.cpg.graph.declarations.RecordDeclaration
import de.fraunhofer.aisec.cpg.graph.functions
import de.fraunhofer.aisec.cpg.graph.nodes
import de.fraunhofer.aisec.cpg.graph.records
import de.fraunhofer.aisec.cpg.graph.statements.expressions.CallExpression
import de.fraunhofer.aisec.cpg.graph.variables
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.GetNodePayload
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.QueryGraphPayload
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.runOnCpg
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.toJson
import de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils.toObject
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun Server.addQueryGraph() {
    val description =
        """Query the Code Property Graph. Supports querying functions, records (classes/structs), calls, variables, and overlays (concepts/operations). Optional name filter and pagination."""
    val inputSchema =
        ToolSchema(
            properties =
                buildJsonObject {
                    putJsonObject("kind") {
                        put("type", "string")
                        put(
                            "description",
                            "What to query: 'functions', 'records', 'calls', 'variables', 'overlays'",
                        )
                    }
                    putJsonObject("name") {
                        put("type", "string")
                        put("description", "Optional: filter by name (substring match)")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "Max results to return (default 50)")
                    }
                    putJsonObject("offset") {
                        put("type", "integer")
                        put("description", "Skip first N results (default 0)")
                    }
                },
            required = listOf("kind"),
        )

    this.addTool(name = "query_graph", description = description, inputSchema = inputSchema) {
        request ->
        request.runOnCpg { result: TranslationResult, request: CallToolRequest ->
            val payload =
                request.arguments?.toObject<QueryGraphPayload>()
                    ?: return@runOnCpg CallToolResult(
                        content =
                            listOf(TextContent("Invalid or missing payload for query_graph tool."))
                    )

            val limit = payload.limit.coerceIn(1, 200)
            val offset = payload.offset.coerceAtLeast(0)

            val items: List<String> =
                when (payload.kind) {
                    "functions" -> {
                        val functions =
                            if (payload.name != null) {
                                result.functions.filter { it.name.localName.contains(payload.name) }
                            } else {
                                result.functions
                            }
                        functions.drop(offset).take(limit).map { it.toJson() }
                    }
                    "records" -> {
                        val records =
                            if (payload.name != null) {
                                result.records.filter { it.name.localName.contains(payload.name) }
                            } else {
                                result.records
                            }
                        records.drop(offset).take(limit).map { it.toJson() }
                    }
                    "calls" -> {
                        val calls =
                            if (payload.name != null) result.callsByName(payload.name)
                            else result.calls
                        calls.drop(offset).take(limit).map { it.toJson() }
                    }
                    "variables" -> {
                        val variables =
                            if (payload.name != null) {
                                result.variables.filter { it.name.localName.contains(payload.name) }
                            } else {
                                result.variables
                            }
                        variables.drop(offset).take(limit).map { it.toJson() }
                    }
                    "overlays" -> {
                        val overlays = result.allChildrenWithOverlays<OverlayNode>()
                        val filtered =
                            if (payload.name != null) {
                                overlays.filter { it.name.localName.contains(payload.name) }
                            } else {
                                overlays
                            }
                        filtered.drop(offset).take(limit).map { it.toJson() }
                    }
                    else ->
                        return@runOnCpg CallToolResult(
                            content =
                                listOf(
                                    TextContent(
                                        "Unknown kind '${payload.kind}'. Use: functions, records, calls, variables, overlays."
                                    )
                                )
                        )
                }

            CallToolResult(content = items.map { TextContent(it) })
        }
    }
}

fun Server.addGetNode() {
    val description =
        "Get full details of a specific node by its ID, including code, location, type, and overlays."
    val inputSchema =
        ToolSchema(
            properties =
                buildJsonObject {
                    putJsonObject("id") {
                        put("type", "string")
                        put("description", "The node's UUID")
                    }
                },
            required = listOf("id"),
        )

    this.addTool(name = "get_node", description = description, inputSchema = inputSchema) { request
        ->
        request.runOnCpg { result: TranslationResult, request: CallToolRequest ->
            val payload =
                request.arguments?.toObject<GetNodePayload>()
                    ?: return@runOnCpg CallToolResult(
                        content = listOf(TextContent("Invalid or missing payload for get_node."))
                    )
            val node = result.nodes.find { it.id.toString() == payload.id }
            val overlay =
                node?.let { null }
                    ?: result.allChildrenWithOverlays<OverlayNode>().find {
                        it.id.toString() == payload.id
                    }
            if (node == null && overlay == null) {
                return@runOnCpg CallToolResult(
                    content = listOf(TextContent("Node not found: ${payload.id}"))
                )
            }

            val json =
                when {
                    overlay != null -> overlay.toJson()
                    node is FunctionDeclaration -> node.toJson()
                    node is RecordDeclaration -> node.toJson()
                    node is CallExpression -> node.toJson()
                    else -> node!!.toJson()
                }

            CallToolResult(content = listOf(TextContent(json)))
        }
    }
}
