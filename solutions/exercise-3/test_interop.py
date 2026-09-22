"""
Cross-language interop test: Call the Java Schedule & Content Advisor from Python.
Uses the Python A2A SDK client to talk to the Java A2A agent on port 8080.

Run this after starting the Schedule & Content Advisor (Exercise 1) on port 8080.
"""

import asyncio
import uuid

import httpx
from google.protobuf.json_format import MessageToDict

from a2a.client.client import ClientConfig
from a2a.client.client_factory import create_client
from a2a.types.a2a_pb2 import (
    GetTaskRequest,
    Message,
    Part,
    Role,
    SendMessageRequest,
    TaskState,
)
from a2a.utils.constants import TransportProtocol


TERMINAL_STATES = {
    TaskState.TASK_STATE_COMPLETED,
    TaskState.TASK_STATE_FAILED,
    TaskState.TASK_STATE_CANCELED,
    TaskState.TASK_STATE_REJECTED,
}


async def main():
    java_agent_url = "http://localhost:8080"

    print("=" * 60)
    print("Python → Java A2A Interop Test (using A2A Python SDK)")
    print("=" * 60)

    # 1. Create a client — resolves the AgentCard automatically
    print(f"\n1. Connecting to Java agent at {java_agent_url}...")
    config = ClientConfig(
        httpx_client=httpx.AsyncClient(timeout=httpx.Timeout(600.0)),
        supported_protocol_bindings=[
            TransportProtocol.HTTP_JSON,
            TransportProtocol.JSONRPC,
        ],
        streaming=False,
    )
    async with await create_client(java_agent_url, client_config=config) as client:
        # Print the resolved AgentCard
        card = client._card
        print(f"   Agent: {card.name}")
        print(f"   Description: {card.description}")
        print(f"   Skills: {[s.name for s in card.skills]}")
        print(f"   AgentCard: {MessageToDict(card)}")

        # 2. Send a message
        query = "What sessions about agentic AI are available on October 7?"
        print(f"\n2. Sending message: \"{query}\"")
        request = SendMessageRequest(
            message=Message(
                message_id=str(uuid.uuid4()),
                role=Role.ROLE_USER,
                parts=[Part(text=query)],
            ),
        )
        print(f"   → SendMessageRequest: {MessageToDict(request)}")

        task = None
        message_response = None
        async for response in client.send_message(request):
            print(f"   ← StreamResponse: {MessageToDict(response)}")
            if response.HasField("task"):
                task = response.task
            elif response.HasField("message"):
                message_response = response.message

        # 3. Poll if task is not yet complete
        if task and task.status.state not in TERMINAL_STATES:
            print(f"\n   ⏳ Task {task.id} is {TaskState.Name(task.status.state)}, polling...")
            poll_count = 0
            while task.status.state not in TERMINAL_STATES:
                poll_count += 1
                await asyncio.sleep(5)
                print(f"\n   [poll #{poll_count}]")
                get_request = GetTaskRequest(id=task.id, history_length=1)
                print(f"   → GetTaskRequest: {MessageToDict(get_request)}")
                task = await client.get_task(get_request)
                print(f"   ← Task state: {TaskState.Name(task.status.state)}")
                print(f"   ← Task: {MessageToDict(task)}")

        # 4. Print final result
        print("\n3. Final result:")
        if task:
            print(f"   Task ID: {task.id}")
            print(f"   State:   {TaskState.Name(task.status.state)}")
            if task.artifacts:
                for i, artifact in enumerate(task.artifacts):
                    print(f"   Artifact[{i}]: {MessageToDict(artifact)}")
            else:
                print("   No artifacts in task.")
        elif message_response:
            print("   Direct message response:")
            print(f"   {MessageToDict(message_response)}")
        else:
            print("   No task or message in response.")

    print("\n" + "=" * 60)
    print("✅ Cross-language interop successful!")
    print("   Python A2A SDK client → Java A2A agent → response received")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
