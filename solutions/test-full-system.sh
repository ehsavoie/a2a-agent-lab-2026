#!/usr/bin/env bash
# Full system smoke test for the DevSphere Concierge
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
echo "  DevSphere — Full System Test"
echo "============================================"
echo

# Test 1: AgentCard discovery for all agents
info "Testing AgentCard discovery..."

for entry in "8080:Schedule & Content Advisor" "8081:Venue & On-Site Operations" "9000:Travel & Logistics" "8090:Orchestrator & Concierge" "8082:Expense & Compliance"; do
    port="${entry%%:*}"
    label="${entry#*:}"
    name=$(curl -s "http://localhost:${port}/.well-known/agent-card.json" 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('name',''))" 2>/dev/null || echo "")
    if [ -n "$name" ]; then
        pass "Port ${port}: ${name}"
    else
        echo -e "${YELLOW}⚠ Port ${port} (${label}): not reachable${NC}"
    fi
done

echo

# Test 2: Schedule & Content Advisor (Java A2A SDK)
info "Testing Schedule & Content Advisor (Java A2A SDK :8080)..."
RESPONSE=$(curl -s -X POST http://localhost:8080/ \
    -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
    -d '{
        "jsonrpc": "2.0",
        "method": "SendMessage",
        "params": {
            "message": {
                "messageId": "msg-1",
                "role": "ROLE_USER",
                "parts": [{"text": "What sessions about agentic AI are available on October 7?"}]
            }
        },
        "id": "test-1"
    }' 2>/dev/null || echo "")

if echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'result' in d" 2>/dev/null; then
    pass "Schedule & Content Advisor responded successfully"
else
    fail "Schedule & Content Advisor failed to respond"
fi

echo

# Test 3: Venue & On-Site Operations Agent (Spring Boot)
info "Testing Venue & On-Site Operations Agent (Spring Boot :8081)..."
RESPONSE=$(curl -s -X POST http://localhost:8081/message:send \
    -H "Content-Type: application/json" \
    -H "A2A-Version: 1.0" \
    -d '{
        "message": {
            "messageId": "msg-1",
            "role": "ROLE_USER",
            "parts": [{"text": "Is Hall B full? Can I get a fast-track entry pass?"}]
        }
    }' 2>/dev/null || echo "")

if echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'result' in d" 2>/dev/null; then
    pass "Venue Agent responded successfully"
else
    fail "Venue Agent failed to respond"
fi

echo

# Test 4: Travel & Logistics Agent (Python)
info "Testing Travel & Logistics Agent (Python :9000)..."
RESPONSE=$(curl -s -X POST http://localhost:9000/ \
    -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
    -d '{
        "jsonrpc": "2.0",
        "method": "SendMessage",
        "params": {
            "message": {
                "messageId": "msg-1",
                "role": "ROLE_USER",
                "parts": [{"text": "My flight was delayed. How do I get to the convention center quickly?"}]
            }
        },
        "id": "test-3"
    }' 2>/dev/null || echo "")

if echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'result' in d" 2>/dev/null; then
    pass "Travel & Logistics Agent responded successfully"
else
    fail "Travel & Logistics Agent failed to respond"
fi

echo

# Test 5: Expense & Compliance Agent (Java A2A SDK)
info "Testing Expense & Compliance Agent (Java A2A SDK :8082)..."
RESPONSE=$(curl -s -X POST http://localhost:8082/ \
    -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
    -d '{
        "jsonrpc": "2.0",
        "method": "SendMessage",
        "params": {
            "message": {
                "messageId": "msg-1",
                "role": "ROLE_USER",
                "parts": [{"text": "Log my taxi receipt: $35 from Airport Express Taxi on 2026-10-07 for transportation"}]
            }
        },
        "id": "test-4"
    }' 2>/dev/null || echo "")

if echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'result' in d" 2>/dev/null; then
    pass "Expense & Compliance Agent responded successfully"
else
    fail "Expense & Compliance Agent failed to respond"
fi

echo

# Test 6: Orchestrator (Maya's full scenario)
info "Testing Orchestrator (:8090) with Maya's scenario..."
info "Sending: 'My flight was delayed... agentic AI and Java agents...'"
RESPONSE=$(curl -s --max-time 180 -X POST http://localhost:8090/ \
    -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
    -d '{
        "jsonrpc": "2.0",
        "method": "SendMessage",
        "params": {
            "message": {
                "messageId": "msg-1",
                "role": "ROLE_USER",
                "parts": [{"text": "My flight was delayed so I missed the morning shuttle. I'\''m interested in agentic AI and Java agents—what talks should I catch today, how do I get to the venue quickly, and can you log my taxi receipt?"}]
            }
        },
        "id": "test-maya"
    }' 2>/dev/null || echo "")

if echo "$RESPONSE" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'result' in d" 2>/dev/null; then
    pass "Orchestrator responded successfully to Maya's scenario"
else
    fail "Orchestrator failed to respond"
fi

echo
echo "============================================"
echo -e "${GREEN}  All tests passed!${NC}"
echo "============================================"
echo
info "Open Grafana at http://localhost:3000 (admin/admin) → Explore → Tempo to see the traces"
