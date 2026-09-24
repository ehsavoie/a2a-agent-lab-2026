#!/usr/bin/env bash
# Start the full DevSphere system and run a smoke test
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PIDS=()

cleanup() {
  echo ""
  echo "Stopping all agents..."
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null
  echo "All agents stopped."
}
trap cleanup EXIT INT TERM

echo "============================================"
echo "  DevSphere — Full System Launch"
echo "============================================"
echo ""

# Check API configuration
echo "Checking API configuration..."
if [[ -n "${OPENAI_API_KEY:-}" ]]; then
  echo "  [OK] OPENAI_API_KEY is set"
else
  echo "  [!!] OPENAI_API_KEY is not set — export a valid OpenAI API key with API billing enabled"
  exit 1
fi

# Check infrastructure
echo "Checking infrastructure..."
if curl -s http://localhost:3000 > /dev/null 2>&1; then
  echo "  [OK] Grafana (LGTM) is running"
else
  echo "  [!!] Grafana (LGTM) is NOT running — start it with: cd $ROOT/exercises/exercise-5-orchestrator && podman-compose up -d"
  exit 1
fi

echo ""

# Start agents
echo "[1/5] Starting Schedule & Content Advisor (Quarkus :8080)..."
cd "$ROOT/exercises/exercise-1-schedule-advisor"
mvn -q quarkus:dev -Dquarkus.console.enabled=false > /tmp/schedule-agent.log 2>&1 &
PIDS+=($!)

echo "[2/5] Starting Venue & On-Site Operations Agent (Spring Boot :8081)..."
cd "$ROOT/exercises/exercise-2-venue-agent"
mvn -q spring-boot:run > /tmp/venue-agent.log 2>&1 &
PIDS+=($!)

echo "[3/5] Starting Travel & Logistics Agent (Python :9000)..."
cd "$ROOT/exercises/exercise-3-travel-agent-python"
pip install -e . --quiet 2>/dev/null
python travel_agent.py > /tmp/travel-agent.log 2>&1 &
PIDS+=($!)

echo "[4/5] Starting Orchestrator & Concierge (Quarkus :8090)..."
cd "$ROOT/exercises/exercise-5-orchestrator"
mvn -q quarkus:dev -Dquarkus.console.enabled=false > /tmp/orchestrator.log 2>&1 &
PIDS+=($!)

echo "[5/5] Building & starting Expense & Compliance Agent (WildFly :8082)..."
cd "$ROOT/exercises/exercise-4-expense-agent"
mvn -q package -Pjsonrpc
"$ROOT/exercises/exercise-4-expense-agent/target/wildfly/bin/standalone.sh" \
  -Djboss.socket.binding.port-offset=2 > /tmp/expense-agent.log 2>&1 &
PIDS+=($!)

echo ""
echo "Waiting for agents to start (45 seconds)..."
sleep 45

# Verify
echo ""
echo "============================================"
echo "  Verifying Agents"
echo "============================================"

ALL_OK=true
for entry in "8080:Schedule & Content Advisor" "8081:Venue Agent" "9000:Travel & Logistics Agent" "8090:Orchestrator" "8082:Expense & Compliance Agent"; do
  port="${entry%%:*}"
  label="${entry#*:}"
  name=$(curl -s "http://localhost:${port}/.well-known/agent-card.json" 2>/dev/null \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('name',''))" 2>/dev/null || echo "")
  if [ -n "$name" ]; then
    echo "  [OK] ${label} (port ${port}): ${name}"
  else
    echo "  [!!] ${label} (port ${port}): NOT READY"
    ALL_OK=false
  fi
done

if [ "$ALL_OK" = false ]; then
  echo ""
  echo "Some agents are not ready. Check logs in /tmp/*-agent.log"
  echo "Press Ctrl+C to stop, or wait and try manually."
  wait
  exit 1
fi

# Send Maya's scenario to the Orchestrator (Schedule + Venue + Travel)
echo ""
echo "============================================"
echo "  Sending Maya's scenario to Orchestrator"
echo "============================================"
echo ""
echo "Query: 'My flight was delayed so I missed the morning shuttle."
echo "        I'm interested in agentic AI and Java agents—"
echo "        what talks should I catch today, and how do I get to"
echo "        the venue quickly?'"
echo ""
echo "(This may take 60-120 seconds due to multiple LLM calls...)"
echo ""

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
        "parts": [{"text": "My flight was delayed so I missed the morning shuttle. I'\''m interested in agentic AI and Java agents—what talks should I catch today, and how do I get to the venue quickly?"}]
      }
    },
    "id": "maya-test-1"
  }' 2>/dev/null || echo '{"error": "request failed"}')

echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"

# Send expense directly to the Expense Agent (standalone)
echo ""
echo "============================================"
echo "  Sending expense to Expense Agent (direct)"
echo "============================================"
echo ""

EXPENSE_RESPONSE=$(curl -s --max-time 120 -X POST http://localhost:8082/ \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "msg-1",
        "role": "ROLE_USER",
        "parts": [{"text": "Log my taxi receipt: $35 from Airport Express Taxi on 2026-10-07 for transportation to the venue"}]
      }
    },
    "id": "maya-expense-1"
  }' 2>/dev/null || echo '{"error": "request failed"}')

echo "$EXPENSE_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$EXPENSE_RESPONSE"

echo ""
echo "============================================"
echo "  Open Grafana to see the traces!"
echo "  http://localhost:3000  (admin/admin)"
echo "  Navigate to Explore → Tempo"
echo "============================================"
echo ""
echo "All agents running. Press Ctrl+C to stop."
wait
