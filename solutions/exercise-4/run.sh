#!/usr/bin/env bash
# Run the Exercise 4 Concierge Orchestrator (Quarkus)
cd "$(dirname "$0")/../../exercises/exercise-4-concierge"

echo "============================================"
echo "  DevConf Concierge Orchestrator"
echo "============================================"
echo ""
echo "Prerequisites (must be running):"
echo "  - Session Agent on port 8080"
echo "  - Travel Tips Agent on port 9000"
echo ""

# Quick check
for port in 8080 9000; do
  if curl -s "http://localhost:${port}/.well-known/agent.json" > /dev/null 2>&1; then
    echo "  [OK] Port ${port} is reachable"
  else
    echo "  [!!] Port ${port} is NOT reachable — start the agent first"
  fi
done

echo ""
echo "Starting Concierge Agent on port 8090..."
echo "AgentCard will be at: http://localhost:8090/.well-known/agent.json"
echo ""
mvn quarkus:dev
