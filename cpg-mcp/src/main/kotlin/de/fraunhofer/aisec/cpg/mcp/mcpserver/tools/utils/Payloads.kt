/*
 * Copyright (c) 2025, Fraunhofer AISEC. All rights reserved.
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
package de.fraunhofer.aisec.cpg.mcp.mcpserver.tools.utils

import kotlinx.serialization.Serializable

@Serializable
data class CpgAnalyzePayload(
    val content: String? = null,
    val extension: String? = null,
    val runPasses: Boolean? = null,
)

@Serializable
data class CpgRunPassPayload(
    /** The FQN of the pass to run. */
    val passName: String,
    /** The ID of the node which should be analyzed by the pass. */
    val nodeId: String,
)

@Serializable data class CpgDataflowPayload(val from: String, val to: String)

/**
 * This class represents information about a pass, including its fully qualified name (FQN), a
 * description, required node type, dependencies, and soft dependencies.
 */
@Serializable
data class PassInfo(
    /** The fully qualified name of the pass. */
    val fqn: String,
    /** A brief description of the pass. */
    val description: String,
    /** The type of node required by the pass. */
    val requiredNodeType: String,
    /**
     * A list of passes whose results are required for this pass to run correctly. These are hard
     * requirements. Note that it may be sufficient to run these passes for the same nodes that this
     * pass should run on and may not require analyzing the whole CPG.
     */
    val dependsOn: List<String>,
    /**
     * A list of passes whose results can enhance the analysis of this pass but are not strictly
     * necessary. These are soft requirements. However, if the passes in this list may be run on the
     * node, this should happen before this pass.
     */
    val softDependencies: List<String>,
)

@Serializable
data class QueryGraphPayload(
    val kind: String,
    /** "functions", "records", "calls", "variables", "overlays" */
    val name: String? = null,
    /** optional name filter (substring match) */
    val limit: Int = 50,
    /** pagination */
    val offset: Int = 0,
)

@Serializable data class GetNodePayload(val id: String)

/** for apply_overlay (flat, one-at-a-time, replaces the nested assignments array */
@Serializable
data class ApplyOverlayPayload(
    val nodeId: String,
    val overlayFqn: String,
    val overlayType: String,
    /** required only for Operations */
    val conceptNodeId: String? = null,
)

/** for suggest_overlays (replaced CpgLLmAnalyzePayload) */
@Serializable data class SuggestOverlaysPayload(val description: String? = null)
