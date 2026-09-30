#!/usr/bin/env bash
# Run the Exercise 2 Venue & On-Site Operations Agent (Spring Boot)
set -euo pipefail
cd "$(dirname "$0")/../../exercises/exercise-2-venue-agent"
echo "Starting Venue & On-Site Operations Agent (Spring Boot) on port 8081..."
echo "AgentCard will be at: http://localhost:8081/.well-known/agent-card.json"
echo ""
mvn spring-boot:run
