"""
Cross-language interop test: Call the Java Session Agent from Python.
Run this after starting the Session Agent (Exercise 1) on port 8080.
"""

import asyncio
import json

import httpx


async def get_agent_card(base_url: str) -> dict:
    async with httpx.AsyncClient() as client:
        response = await client.get(f"{base_url}/.well-known/agent.json")
        response.raise_for_status()
        return response.json()


async def send_message(base_url: str, text: str) -> dict:
    payload = {
        "jsonrpc": "2.0",
        "method": "message/send",
        "params": {
            "message": {
                "role": "user",
                "parts": [{"type": "text", "text": text}],
            }
        },
        "id": "py-test-1",
    }
    async with httpx.AsyncClient(timeout=60.0) as client:
        response = await client.post(base_url, json=payload)
        response.raise_for_status()
        return response.json()


async def main():
    java_agent_url = "http://localhost:8080"

    print("=" * 60)
    print("Python → Java A2A Interop Test")
    print("=" * 60)

    print("\n1. Fetching Java Session Agent's AgentCard...")
    card = await get_agent_card(java_agent_url)
    print(f"   Agent: {card.get('name', 'unknown')}")
    print(f"   Skills: {[s['name'] for s in card.get('skills', [])]}")

    print("\n2. Sending message to Java Session Agent...")
    result = await send_message(java_agent_url, "What sessions are about AI?")
    print(f"   Response: {json.dumps(result, indent=2)[:500]}")

    print("\n✅ Cross-language interop successful!")
    print("   Python client → Java A2A agent → response received")


if __name__ == "__main__":
    asyncio.run(main())
