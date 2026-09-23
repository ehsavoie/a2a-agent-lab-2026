#!/usr/bin/env bash
# Run the Exercise 4 Orchestrator & Concierge (Quarkus)
cd "$(dirname "$0")/../../exercises/exercise-4-orchestrator"

echo "============================================"
echo "  DevSphere Orchestrator & Concierge"
echo "============================================"
echo ""
echo "Prerequisites (must be running):"
echo "  - Schedule & Content Advisor on port 8080"
echo "  - Venue & On-Site Operations Agent on port 8081"
echo "  - Travel & Logistics Agent on port 9000"
echo ""

# Quick check
for port in 8080 8081 9000; do
  if curl -s "http://localhost:${port}/.well-known/agent-card.json" > /dev/null 2>&1; then
    echo "  [OK] Port ${port} is reachable"
  else
    echo "  [!!] Port ${port} is NOT reachable — start the agent first"
  fi
done

echo ""
echo "Starting Orchestrator on port 8090..."
echo "AgentCard will be at: http://localhost:8090/.well-known/agent-card.json"
echo ""
mvn quarkus:dev
