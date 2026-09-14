"""
DevConf 2026 Travel Tips Agent — A2A Python Agent

This agent provides local travel tips, restaurant recommendations,
and transit information for conference attendees.
"""

import json
from typing import Any

from a2a.server.agent_execution import AgentExecution, RequestContext
from a2a.server.apps import A2AStarletteApplication
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.types import AgentCard, AgentCapabilities, AgentSkill, Part, TextPart
import uvicorn


TRAVEL_DATA = {
    "restaurants": [
        {
            "name": "The Code Kitchen",
            "cuisine": "International",
            "distance": "200m from convention center",
            "price": "$$",
            "rating": 4.5,
            "description": "Developer-themed restaurant with great lunch specials. Try the 'Merge Conflict' burger.",
        },
        {
            "name": "Sakura Bytes",
            "cuisine": "Japanese",
            "distance": "500m from convention center",
            "price": "$$$",
            "rating": 4.7,
            "description": "Authentic Japanese cuisine with private dining rooms perfect for team dinners.",
        },
        {
            "name": "Pasta Pipeline",
            "cuisine": "Italian",
            "distance": "300m from convention center",
            "price": "$$",
            "rating": 4.3,
            "description": "Fresh pasta made daily. Great vegetarian options. Popular with conference attendees.",
        },
        {
            "name": "Green Thread Café",
            "cuisine": "Vegan/Vegetarian",
            "distance": "150m from convention center",
            "price": "$",
            "rating": 4.6,
            "description": "Fully plant-based café with excellent coffee and quick bites between sessions.",
        },
        {
            "name": "The Overflow Pub",
            "cuisine": "Gastropub",
            "distance": "400m from convention center",
            "price": "$$",
            "rating": 4.4,
            "description": "Craft beers and hearty food. Hosts the unofficial DevConf after-party every evening.",
        },
    ],
    "transit": {
        "airport_to_venue": "Take the Metro Blue Line from the airport to Central Station (25 min), then walk 10 minutes to the convention center.",
        "metro_nearest": "Central Station — 10 minute walk to convention center. Lines: Blue, Green.",
        "taxi": "Taxis available outside the convention center. Average fare to airport: $25-35.",
        "parking": "Convention center parking garage: $15/day. Enter from Oak Street.",
    },
    "local_tips": [
        "The convention center Wi-Fi password changes daily — check the info desk.",
        "Best coffee near the venue: Green Thread Café (150m) or the lobby barista.",
        "The rooftop terrace on the 3rd floor is open during breaks — great city views.",
        "Power outlets are available under every other seat in the main hall.",
        "The speaker lounge on the 2nd floor has quiet workspaces open to all attendees.",
    ],
}


def search_restaurants(query: str) -> str:
    query_lower = query.lower()
    matches = []
    for r in TRAVEL_DATA["restaurants"]:
        if (
            query_lower in r["cuisine"].lower()
            or query_lower in r["name"].lower()
            or query_lower in r["description"].lower()
            or any(word in r["description"].lower() for word in query_lower.split())
        ):
            matches.append(r)

    if not matches:
        matches = TRAVEL_DATA["restaurants"]

    lines = []
    for r in matches:
        lines.append(
            f"**{r['name']}** ({r['cuisine']}) — {r['price']} — ⭐ {r['rating']}\n"
            f"  📍 {r['distance']}\n"
            f"  {r['description']}"
        )
    return "\n\n".join(lines)


def get_transit_info(query: str) -> str:
    query_lower = query.lower()
    if "airport" in query_lower or "fly" in query_lower:
        return TRAVEL_DATA["transit"]["airport_to_venue"]
    if "metro" in query_lower or "train" in query_lower or "subway" in query_lower:
        return TRAVEL_DATA["transit"]["metro_nearest"]
    if "taxi" in query_lower or "uber" in query_lower or "ride" in query_lower:
        return TRAVEL_DATA["transit"]["taxi"]
    if "park" in query_lower or "car" in query_lower or "drive" in query_lower:
        return TRAVEL_DATA["transit"]["parking"]
    return "\n".join(
        f"- **{k.replace('_', ' ').title()}**: {v}"
        for k, v in TRAVEL_DATA["transit"].items()
    )


def get_local_tips() -> str:
    return "\n".join(f"💡 {tip}" for tip in TRAVEL_DATA["local_tips"])


def answer_query(query: str) -> str:
    query_lower = query.lower()

    if any(w in query_lower for w in ["eat", "food", "restaurant", "dinner", "lunch", "breakfast", "cuisine"]):
        return f"Here are restaurant recommendations near the convention center:\n\n{search_restaurants(query)}"

    if any(w in query_lower for w in ["transit", "transport", "metro", "airport", "taxi", "drive", "park", "get there", "directions"]):
        return f"Here's transit information:\n\n{get_transit_info(query)}"

    if any(w in query_lower for w in ["tip", "advice", "know", "helpful"]):
        return f"Here are some helpful tips for DevConf attendees:\n\n{get_local_tips()}"

    return (
        f"Here's what I can help with:\n\n"
        f"🍽️ **Restaurants nearby:**\n{search_restaurants(query)}\n\n"
        f"🚇 **Getting around:**\n{get_transit_info(query)}\n\n"
        f"💡 **Local tips:**\n{get_local_tips()}"
    )


class TravelAgentExecution(AgentExecution):
    """Handles incoming A2A messages and returns travel tips."""

    async def execute(self, context: RequestContext, event_queue) -> None:
        user_message = ""
        if context.message and context.message.parts:
            for part in context.message.parts:
                if isinstance(part.root, TextPart):
                    user_message = part.root.text
                    break

        response_text = answer_query(user_message) if user_message else answer_query("general")

        await event_queue.enqueue_event(
            context.build_success_response(
                parts=[Part(root=TextPart(text=response_text))]
            )
        )


def build_agent_card() -> AgentCard:
    return AgentCard(
        name="Travel Tips Agent",
        description="Provides local travel tips, restaurant recommendations, and transit information for DevConf 2026 attendees.",
        url="http://localhost:9000",
        version="1.0.0",
        capabilities=AgentCapabilities(streaming=False, pushNotifications=False),
        skills=[
            AgentSkill(
                id="restaurant-search",
                name="Restaurant Search",
                description="Find restaurants near the convention center by cuisine, price, or dietary preference.",
                tags=["food", "restaurants", "dining"],
                examples=[
                    "Where should I eat near the convention center?",
                    "Any good Japanese restaurants nearby?",
                    "Vegetarian lunch options?",
                ],
            ),
            AgentSkill(
                id="transit-info",
                name="Transit Information",
                description="Get directions and transit information to and from the convention center.",
                tags=["transit", "directions", "airport", "metro"],
                examples=[
                    "How do I get from the airport to the venue?",
                    "Where is the nearest metro station?",
                    "Is there parking at the convention center?",
                ],
            ),
            AgentSkill(
                id="local-tips",
                name="Local Tips",
                description="Helpful tips for conference attendees about the venue and surroundings.",
                tags=["tips", "venue", "wifi", "coffee"],
                examples=[
                    "Any tips for first-time attendees?",
                    "Where can I find good coffee nearby?",
                ],
            ),
        ],
        defaultInputModes=["text"],
        defaultOutputModes=["text"],
    )


def main():
    agent_card = build_agent_card()
    agent_execution = TravelAgentExecution()

    request_handler = DefaultRequestHandler(
        agent_execution=agent_execution,
        agent_card=agent_card,
    )

    app = A2AStarletteApplication(
        agent_card=agent_card,
        http_handler=request_handler,
    )

    print("🌍 Travel Tips Agent starting on http://localhost:9000")
    print("📋 Agent Card: http://localhost:9000/.well-known/agent.json")
    uvicorn.run(app.build(), host="0.0.0.0", port=9000)


if __name__ == "__main__":
    main()
