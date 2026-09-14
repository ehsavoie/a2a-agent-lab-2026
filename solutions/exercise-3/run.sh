#!/usr/bin/env bash
# Run the Exercise 3 Travel Tips Agent (Python)
cd "$(dirname "$0")/../../exercises/exercise-3-travel-agent-python"

echo "Installing dependencies..."
pip install -e . --quiet 2>/dev/null

echo "Starting Travel Tips Agent (Python) on port 9000..."
echo "AgentCard will be at: http://localhost:9000/.well-known/agent.json"
echo ""
python travel_agent.py
