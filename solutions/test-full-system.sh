#!/usr/bin/env bash
# Full system smoke test for the DevConf Concierge
# Run this after all agents are started.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }
info() { echo -e "${YELLOW}→ $1${NC}"; }

echo "============================================"
echo "  DevConf Concierge — Full System Test"
echo "============================================"
echo

# Test 1: AgentCard discovery for all agents
info "Testing AgentCard discovery..."

for port in 8080 8081 8082 9000 8090; do
    name=$(curl -s "http://localhost:${port}/.well-known/agent.json" 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('name',''))" 2>/dev/null || echo "")
    if [ -n "$name" ]; then
        pass "Port ${port}: ${name}"
    else
        echo -e "${YELLOW}⚠ Port ${port}: not reachable (agent may not be running)${NC}"
    fi
done

echo

# Test 2: Session Agent (Quarkus)
info "Testing Session Agent (Quarkus :8080)..."
RESPONSE=$(curl -s -X POST http://localhost:8080 \
    -H "Content-Type: application/json" \
    -d '{
        "jsonrpc": "2.0",
        "method": "message/send",
        "params": {
            "message": {
                "role": "user",
                "parts": [{"type": "text", "text": "What AI sessions are available?"}]
            }
        },
        "id": "test-1"
    }' 2>/dev/null || echo "")

if echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'result' in d" 2>/dev/null; then
    pass "Session Agent responded successfully"
else
    fail "Session Agent failed to respond"
fi

echo

# Test 3: Travel Tips Agent (Python)
info "Testing Travel Tips Agent (Python :9000)..."
RESPONSE=$(curl -s -X POST http://localhost:9000 \
    -H "Content-Type: application/json" \
    -d '{
        "jsonrpc": "2.0",
        "method": "message/send",
        "params": {
            "message": {
                "role": "user",
                "parts": [{"type": "text", "text": "Where should I eat near the convention center?"}]
            }
        },
        "id": "test-2"
    }' 2>/dev/null || echo "")

if echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'result' in d" 2>/dev/null; then
    pass "Travel Tips Agent responded successfully"
else
    fail "Travel Tips Agent failed to respond"
fi

echo

# Test 4: Concierge Orchestrator
info "Testing Concierge Orchestrator (:8090)..."
info "Sending complex query: 'I arrive Thursday, what sessions and where to eat?'"
RESPONSE=$(curl -s --max-time 120 -X POST http://localhost:8090 \
    -H "Content-Type: application/json" \
    -d '{
        "jsonrpc": "2.0",
        "method": "message/send",
        "params": {
            "message": {
                "role": "user",
                "parts": [{"type": "text", "text": "I am a Java developer arriving Thursday. What sessions should I attend and where should I have dinner afterward?"}]
            }
        },
        "id": "test-3"
    }' 2>/dev/null || echo "")

if echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'result' in d" 2>/dev/null; then
    pass "Concierge Orchestrator responded successfully"
else
    fail "Concierge Orchestrator failed to respond"
fi

echo
echo "============================================"
echo -e "${GREEN}  All tests passed!${NC}"
echo "============================================"
echo
info "Open Jaeger UI at http://localhost:16686 to see the traces"
