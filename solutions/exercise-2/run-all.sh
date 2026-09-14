#!/usr/bin/env bash
# Start all three Session Agent runtimes in background
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
echo "  Starting 3 Session Agent runtimes"
echo "============================================"

echo ""
echo "[1/3] Starting Quarkus agent on port 8080..."
cd "$ROOT/exercises/exercise-1-session-agent"
mvn -q quarkus:dev -Dquarkus.console.enabled=false &
PIDS+=($!)

echo "[2/3] Starting Spring Boot agent on port 8081..."
cd "$ROOT/exercises/exercise-2a-spring-boot"
mvn -q spring-boot:run &
PIDS+=($!)

echo "[3/3] Building and starting WildFly agent on port 8082..."
cd "$ROOT/exercises/exercise-2b-ja2a"
mvn -q package -Dsession.data.path="$ROOT/conference-data/sessions.json"
"$ROOT/exercises/exercise-2b-ja2a/target/wildfly/bin/standalone.sh" \
  -Djboss.socket.binding.port-offset=2 \
  -Dsession.data.path="$ROOT/conference-data/sessions.json" &
PIDS+=($!)

echo ""
echo "Waiting for agents to start..."
sleep 15

echo ""
echo "============================================"
echo "  Verifying AgentCards"
echo "============================================"

for port in 8080 8081 8082; do
  name=$(curl -s "http://localhost:${port}/.well-known/agent.json" 2>/dev/null \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('name',''))" 2>/dev/null || echo "NOT READY")
  echo "  Port ${port}: ${name}"
done

echo ""
echo "All agents running. Press Ctrl+C to stop."
wait
