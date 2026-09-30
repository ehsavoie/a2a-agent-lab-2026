#!/usr/bin/env bash
# Run the Exercise 3 Travel & Logistics Agent (Python)
cd "$(dirname "$0")"

echo "Installing dependencies..."
pip install -e . --quiet 2>/dev/null

echo "Starting Travel & Logistics Agent (Python) on port 9000..."
echo "AgentCard will be at: http://localhost:9000/.well-known/agent-card.json"
echo ""
echo "To run the Java → Python client (in another terminal):"
echo "  cd java-client && mvn compile exec:java"
echo ""
python travel_agent.py
