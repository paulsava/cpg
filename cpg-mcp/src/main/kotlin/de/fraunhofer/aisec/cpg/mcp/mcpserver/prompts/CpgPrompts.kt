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
package de.fraunhofer.aisec.cpg.mcp.mcpserver.prompts

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.*

fun Server.addCpgPrompts() {
    addPrompt(
        name = "security-analysis",
        description =
            "Analyze CPG nodes for security-relevant concepts and operations. Use after running analyze_code and query_graph.",
        arguments =
            listOf(
                PromptArgument(
                    name = "focus",
                    description =
                        "Optional focus area (e.g., 'authentication', 'file I/O', 'network')",
                    required = false,
                )
            ),
    ) {
        GetPromptResult(
            description = "Security analysis workflow for CPG nodes",
            messages =
                listOf(
                    PromptMessage(
                        role = Role.User,
                        content =
                            TextContent(
                                buildString {
                                    appendLine(
                                        "Analyze the CPG nodes for security-relevant patterns."
                                    )
                                    appendLine()
                                    appendLine("## Workflow")
                                    appendLine(
                                        "1. Read the 'cpg://docs/available-concepts' and 'cpg://docs/available-operations' resources"
                                    )
                                    appendLine("2. Use query_graph to list functions and calls")
                                    appendLine(
                                        "3. For each security-relevant node, suggest an overlay:"
                                    )
                                    appendLine(
                                        "   - Concepts mark 'what something IS' (data, secrets, credentials)"
                                    )
                                    appendLine(
                                        "   - Operations mark 'what something DOES' (HTTP requests, file writes, encryption)"
                                    )
                                    appendLine("4. Apply overlays one at a time with apply_overlay")
                                    appendLine(
                                        "5. Use check_dataflow to find data flow paths between concepts"
                                    )
                                    appendLine()
                                    appendLine("## Rules")
                                    appendLine(
                                        "- Operations MUST reference an existing Concept via conceptNodeId"
                                    )
                                    appendLine(
                                        "- Concept-Operation pairs must be from the same domain"
                                    )
                                    appendLine(
                                        "- Use fully qualified class names from the available concepts/operations resources"
                                    )
                                    appendLine("- WAIT for user approval before applying overlays")
                                }
                            ),
                    )
                ),
        )
    }
}
