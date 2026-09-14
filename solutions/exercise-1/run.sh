#!/usr/bin/env bash
# Run the Exercise 1 Session Agent (Quarkus)
cd "$(dirname "$0")/../../exercises/exercise-1-session-agent"
echo "Starting Session Agent (Quarkus) on port 8080..."
echo "AgentCard will be at: http://localhost:8080/.well-known/agent.json"
echo ""
mvn quarkus:dev
