#!/usr/bin/env bash
# Run the Exercise 1 Schedule & Content Advisor (Quarkus)
set -euo pipefail
cd "$(dirname "$0")"
echo "Starting Schedule & Content Advisor (Quarkus)..."
echo "AgentCard will be at: http://localhost:8080/.well-known/agent-card.json"
echo ""
mvn quarkus:dev
