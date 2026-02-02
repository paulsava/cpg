# CPG MCP Server

## Available Tools

### Core Analysis
- **`analyze_code`**: Parse code and build a Code Property Graph (CPG)
- **`get_status`**: Show current analysis state, passes run, overlays applied
- **`run_pass`**: Run a specific analysis pass on a node
- **`check_dataflow`**: Perform dataflow analysis between concepts

### Query and Tagging
- **`query_graph`**: Query functions, records, calls, variables, or overlays
- **`get_node`**: Get full details for a node by ID
- **`suggest_overlays`**: Provide overlay suggestions and candidate nodes
- **`apply_overlay`**: Apply a single concept or operation to a node

## Resources
- **`cpg://docs/graph-model`**: CPG graph model documentation
- **`cpg://docs/available-concepts`**: All available concept classes
- **`cpg://docs/available-operations`**: All available operation classes
- **`cpg://docs/passes`**: Pass catalog with dependencies and required node types

## Prompts
- **`security-analysis`**: Security analysis workflow for applying overlays

## Setup

The MCP server can be used via two transport types:

- **Standard I/O Mode** and
- **Server-Sent Events (SSE)**.

The current implementation uses stdio since Claude Desktop only supports this transport type.

```bash
./gradlew :cpg-mcp:installDist
```

1. Open Claude Desktop
2. Go to Settings -> Developer -> Edit Config
3. Add the following configuration to the `mcpServers` section:

```json
    {
  "mcpServers": {
    "cpg": {
      "command": "/path/to/cpg-mcp/build/install/cpg-mcp/bin/cpg-mcp"
    }
  }
}
```

4. If you're navigating to the config file outside the app:
    - On Linux, it is usually located at `~/.config/claude-desktop/config.json`.
    - On macOS, it is typically at `~/Library/Application Support/Claude Desktop/config.json`.
5. Open the file in a text editor
6. Paste the configuration above into the `mcpServers` section
7. Save the file and restart Claude Desktop

## Usage

### Step 1: Analyze Code (`analyze_code`)

```json
{
  "tool": "analyze_code",
  "arguments": {
    "content": "def read_user_data():\n    with open('/etc/passwd') as f:\n        return f.read()",
    "extension": "py",
    "runPasses": true
  }
}
```

**Response (summary):**

```json
{
  "totalNodes": 15,
  "functions": 3,
  "variables": 5,
  "callExpressions": 2,
  "records": 0,
  "topLevelDeclarations": ["read_user_data"]
}
```

### Step 2: Query the Graph (`query_graph`)

```json
{
  "tool": "query_graph",
  "arguments": {
    "kind": "functions",
    "limit": 10
  }
}
```

### Step 3: Suggest Overlays (`suggest_overlays`)

```json
{
  "tool": "suggest_overlays",
  "arguments": {
    "description": "authentication"
  }
}
```

### Step 4: Apply an Overlay (`apply_overlay`)

```json
{
  "tool": "apply_overlay",
  "arguments": {
    "nodeId": "<node-id>",
    "overlayFqn": "de.fraunhofer.aisec.cpg.graph.concepts.auth.Credential",
    "overlayType": "Concept"
  }
}
```

### Step 5: Check Dataflow (`check_dataflow`)

```json
{
  "tool": "check_dataflow",
  "arguments": {
    "from": "Credential",
    "to": "HttpRequest"
  }
}
```
