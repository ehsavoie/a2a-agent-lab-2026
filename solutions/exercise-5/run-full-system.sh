#!/usr/bin/env bash
# Start the full DevConf Concierge system and run a smoke test
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
echo "  DevConf Concierge — Full System Launch"
echo "============================================"
echo ""

# Check infrastructure
echo "Checking infrastructure..."
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
  echo "  [OK] Ollama is running"
else
  echo "  [!!] Ollama is NOT running — start it with: cd $ROOT && podman-compose up -d"
  exit 1
fi

if curl -s http://localhost:16686 > /dev/null 2>&1; then
  echo "  [OK] Jaeger is running"
else
  echo "  [!!] Jaeger is NOT running — start it with: cd $ROOT && podman-compose up -d"
  exit 1
fi

echo ""

# Start agents
echo "[1/3] Starting Session Agent (Quarkus) on port 8080..."
cd "$ROOT/exercises/exercise-1-session-agent"
mvn -q quarkus:dev -Dquarkus.console.enabled=false > /tmp/session-agent.log 2>&1 &
PIDS+=($!)

echo "[2/3] Starting Travel Tips Agent (Python) on port 9000..."
cd "$ROOT/exercises/exercise-3-travel-agent-python"
pip install -e . --quiet 2>/dev/null
python travel_agent.py > /tmp/travel-agent.log 2>&1 &
PIDS+=($!)

echo "[3/3] Starting Concierge Agent on port 8090..."
cd "$ROOT/exercises/exercise-4-concierge"
mvn -q quarkus:dev -Dquarkus.console.enabled=false > /tmp/concierge-agent.log 2>&1 &
PIDS+=($!)

echo ""
echo "Waiting for agents to start (30 seconds)..."
sleep 30

# Verify
echo ""
echo "============================================"
echo "  Verifying Agents"
echo "============================================"

ALL_OK=true
for entry in "8080:Session Agent" "9000:Travel Tips Agent" "8090:Concierge Agent"; do
  port="${entry%%:*}"
  label="${entry#*:}"
  name=$(curl -s "http://localhost:${port}/.well-known/agent.json" 2>/dev/null \
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

# Send test query
echo ""
echo "============================================"
echo "  Sending test query to Concierge"
echo "============================================"
echo ""
echo "Query: 'I am a Java developer arriving Thursday."
echo "        What sessions should I attend and where should I eat?'"
echo ""
echo "(This may take 30-60 seconds due to multiple LLM calls...)"
echo ""

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
    "id": "full-test-1"
  }' 2>/dev/null || echo '{"error": "request failed"}')

echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"

echo ""
echo "============================================"
echo "  Open Jaeger to see the trace!"
echo "  http://localhost:16686"
echo "============================================"
echo ""
echo "All agents running. Press Ctrl+C to stop."
wait
