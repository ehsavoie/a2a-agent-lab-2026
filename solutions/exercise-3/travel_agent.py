"""
DevSphere 2026 Travel & Logistics Agent — A2A Python Agent

Connects to flight APIs, local transit, and hotel systems.
Handles travel logistics, transit comparison, and receipt extraction
for expense reporting.
"""

import json
from datetime import datetime
from typing import Any

from a2a.server.agent_execution import AgentExecution, RequestContext
from a2a.server.apps import A2AStarletteApplication
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.types import AgentCard, AgentCapabilities, AgentSkill, Part, TextPart
import uvicorn


TRAVEL_DATA = {
    "flights": [
        {
            "flight": "UA 1742",
            "origin": "SFO",
            "destination": "PRG",
            "scheduled_arrival": "06:15",
            "actual_arrival": "08:20",
            "delay_minutes": 125,
            "status": "DELAYED",
            "gate": "B22",
            "terminal": "Terminal 2",
            "note": "Delayed due to late-arriving aircraft from LAX.",
        },
        {
            "flight": "LH 1394",
            "origin": "FRA",
            "destination": "PRG",
            "scheduled_arrival": "07:30",
            "actual_arrival": "07:25",
            "delay_minutes": 0,
            "status": "ON TIME",
            "gate": "A14",
            "terminal": "Terminal 1",
            "note": "Arrived early.",
        },
        {
            "flight": "BA 856",
            "origin": "LHR",
            "destination": "PRG",
            "scheduled_arrival": "09:00",
            "actual_arrival": "09:45",
            "delay_minutes": 45,
            "status": "DELAYED",
            "gate": "C8",
            "terminal": "Terminal 2",
            "note": "Minor delay due to air traffic congestion.",
        },
        {
            "flight": "AF 1382",
            "origin": "CDG",
            "destination": "PRG",
            "scheduled_arrival": "08:00",
            "actual_arrival": "08:00",
            "delay_minutes": 0,
            "status": "ON TIME",
            "gate": "A6",
            "terminal": "Terminal 1",
            "note": "",
        },
    ],
    "transit": {
        "options": [
            {
                "mode": "Rideshare (Bolt/Uber)",
                "duration_minutes": 15,
                "cost": "$35",
                "availability": "Available now",
                "disruption": None,
                "recommendation": "Fastest option. Pickup at Terminal arrivals, Door 3.",
            },
            {
                "mode": "Airport Express Train (AE)",
                "duration_minutes": 30,
                "cost": "$12",
                "availability": "Every 30 min",
                "disruption": "⚠️ Signal fault between Airport and Hlavní nádraží — trains running at reduced frequency, estimated 45 min today.",
                "recommendation": "Budget option but currently disrupted. Add 15 min buffer.",
            },
            {
                "mode": "Taxi (metered)",
                "duration_minutes": 20,
                "cost": "$40–50",
                "availability": "Rank outside arrivals",
                "disruption": None,
                "recommendation": "Reliable but more expensive than rideshare. Insist on the meter.",
            },
            {
                "mode": "Hotel Shuttle",
                "duration_minutes": 35,
                "cost": "Free (with hotel booking)",
                "availability": "Check with your hotel",
                "disruption": None,
                "recommendation": "Free if your hotel provides one, but slower due to multiple stops.",
            },
        ],
        "metro_nearest": "Vyšehrad Station — 8 minute walk to the convention center. Lines: C (Red).",
        "parking": "Convention center parking garage: $15/day. Enter from Oak Street. Electric vehicle chargers on Level P2.",
    },
    "hotels": [
        {
            "name": "Hotel DevSphere Grand",
            "distance": "200m (convention center attached)",
            "price_per_night": "$189",
            "availability": "3 rooms left",
            "rating": 4.6,
            "amenities": "Wi-Fi, gym, rooftop bar, conference shuttle",
        },
        {
            "name": "The Coder's Inn",
            "distance": "500m",
            "price_per_night": "$129",
            "availability": "Available",
            "rating": 4.4,
            "amenities": "Wi-Fi, co-working lounge, breakfast included",
        },
        {
            "name": "Central Station Hostel",
            "distance": "1.2km (near metro)",
            "price_per_night": "$49",
            "availability": "Available",
            "rating": 4.1,
            "amenities": "Wi-Fi, shared kitchen, locker storage",
        },
        {
            "name": "Riverside Boutique Hotel",
            "distance": "800m",
            "price_per_night": "$159",
            "availability": "Limited — 1 room",
            "rating": 4.8,
            "amenities": "Wi-Fi, spa, river-view rooms, organic breakfast",
        },
    ],
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
            "description": "Craft beers and hearty food. Hosts the unofficial DevSphere after-party every evening.",
        },
    ],
    "local_tips": [
        "The convention center Wi-Fi password changes daily — check the info desk.",
        "Best coffee near the venue: Green Thread Café (150m) or the lobby barista.",
        "The rooftop terrace on the 3rd floor is open during breaks — great city views.",
        "Power outlets are available under every other seat in the main hall.",
        "The speaker lounge on the 2nd floor has quiet workspaces open to all attendees.",
    ],
}


def check_flight_status(query: str) -> str:
    query_upper = query.upper()
    matches = []
    for f in TRAVEL_DATA["flights"]:
        flight_id = f["flight"].replace(" ", "")
        query_clean = query_upper.replace(" ", "")
        if flight_id in query_clean or f["origin"] in query_upper:
            matches.append(f)

    if not matches:
        matches = TRAVEL_DATA["flights"]

    lines = []
    for f in matches:
        status_icon = "✅" if f["status"] == "ON TIME" else "⚠️"
        delay_info = ""
        if f["delay_minutes"] > 0:
            delay_info = f" (delayed {f['delay_minutes']} min)"
        lines.append(
            f"{status_icon} **{f['flight']}** ({f['origin']} → {f['destination']})\n"
            f"  Scheduled: {f['scheduled_arrival']} | Actual: {f['actual_arrival']}{delay_info}\n"
            f"  Status: {f['status']} | Gate: {f['gate']} | {f['terminal']}"
        )
        if f["note"]:
            lines[-1] += f"\n  Note: {f['note']}"
    return "\n\n".join(lines)


def get_transit_options(query: str) -> str:
    lines = ["**Transit options from the airport to the convention center:**\n"]
    best_option = None
    best_time = 999

    for opt in TRAVEL_DATA["transit"]["options"]:
        effective_time = opt["duration_minutes"]
        disruption_note = ""
        if opt["disruption"]:
            disruption_note = f"\n  {opt['disruption']}"
            if "45 min" in opt["disruption"]:
                effective_time = 45

        if effective_time < best_time:
            best_time = effective_time
            best_option = opt["mode"]

        lines.append(
            f"🚗 **{opt['mode']}** — {opt['cost']} — ~{effective_time} min\n"
            f"  {opt['recommendation']}{disruption_note}"
        )

    lines.append(f"\n💡 **Recommendation:** {best_option} is the fastest option today (~{best_time} min).")

    if any(w in query.lower() for w in ["metro", "station", "walk"]):
        lines.append(f"\n🚇 Nearest metro: {TRAVEL_DATA['transit']['metro_nearest']}")

    return "\n\n".join(lines)


def search_hotels(query: str) -> str:
    query_lower = query.lower()
    matches = []
    for h in TRAVEL_DATA["hotels"]:
        if (
            any(w in h["name"].lower() for w in query_lower.split())
            or "cheap" in query_lower and "$" in h["price_per_night"][:2]
            or "luxury" in query_lower and h["rating"] >= 4.7
        ):
            matches.append(h)

    if not matches:
        matches = TRAVEL_DATA["hotels"]

    lines = []
    for h in matches:
        avail_icon = "🟢" if "Available" in h["availability"] else "🟡"
        lines.append(
            f"{avail_icon} **{h['name']}** — {h['price_per_night']}/night — ⭐ {h['rating']}\n"
            f"  📍 {h['distance']} | {h['availability']}\n"
            f"  Amenities: {h['amenities']}"
        )
    return "\n\n".join(lines)


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


def get_local_tips() -> str:
    return "\n".join(f"💡 {tip}" for tip in TRAVEL_DATA["local_tips"])


def extract_receipt(query: str) -> str:
    query_lower = query.lower()

    vendor = "Unknown vendor"
    amount = "0.00"
    currency = "USD"
    category = "transportation"
    date = datetime.now().strftime("%Y-%m-%d")

    if any(w in query_lower for w in ["taxi", "cab"]):
        vendor = "City Taxi Co."
        amount = "42.50"
        category = "ground_transportation"
    elif any(w in query_lower for w in ["uber", "lyft", "bolt", "rideshare", "ride"]):
        vendor = "Bolt Rideshare"
        amount = "35.00"
        category = "ground_transportation"
    elif any(w in query_lower for w in ["train", "metro", "rail"]):
        vendor = "Airport Express Rail"
        amount = "12.00"
        category = "public_transit"
    elif any(w in query_lower for w in ["hotel", "room"]):
        vendor = "Hotel DevSphere Grand"
        amount = "189.00"
        category = "lodging"
    elif any(w in query_lower for w in ["food", "restaurant", "meal", "dinner", "lunch"]):
        vendor = "Conference Dining"
        amount = "28.50"
        category = "meals"

    for word in query_lower.split():
        word_clean = word.strip("$€£,.")
        try:
            parsed = float(word_clean)
            amount = f"{parsed:.2f}"
            break
        except ValueError:
            pass

    receipt = {
        "vendor": vendor,
        "amount": amount,
        "currency": currency,
        "date": date,
        "category": category,
        "status": "pending_review",
        "confidence": "high" if vendor != "Unknown vendor" else "low",
    }

    return (
        f"📄 **Receipt extracted:**\n\n"
        f"```\n"
        f"Vendor:   {receipt['vendor']}\n"
        f"Amount:   {receipt['currency']} {receipt['amount']}\n"
        f"Date:     {receipt['date']}\n"
        f"Category: {receipt['category']}\n"
        f"Status:   {receipt['status']}\n"
        f"```\n\n"
        f"This receipt data is ready to be forwarded to the Expense & Compliance Agent "
        f"for audit-ready processing.\n\n"
        f"_Structured payload:_\n```json\n{json.dumps(receipt, indent=2)}\n```"
    )


def answer_query(query: str) -> str:
    query_lower = query.lower()

    if any(w in query_lower for w in ["flight", "delay", "gate", "landing", "arrive", "arrival"]):
        return f"Here's the flight status information:\n\n{check_flight_status(query)}"

    if any(w in query_lower for w in ["receipt", "expense", "fare", "reimburse", "log"]):
        return extract_receipt(query)

    if any(w in query_lower for w in ["hotel", "stay", "accommodation", "room", "book"]):
        return f"Here are nearby hotels:\n\n{search_hotels(query)}"

    if any(w in query_lower for w in ["transit", "transport", "metro", "airport", "taxi",
                                       "drive", "park", "get there", "directions",
                                       "shuttle", "rideshare", "uber", "bolt",
                                       "commute", "venue", "how do i get"]):
        return f"Here are your transit options:\n\n{get_transit_options(query)}"

    if any(w in query_lower for w in ["eat", "food", "restaurant", "dinner", "lunch",
                                       "breakfast", "cuisine"]):
        return f"Here are restaurant recommendations near the convention center:\n\n{search_restaurants(query)}"

    if any(w in query_lower for w in ["tip", "advice", "know", "helpful"]):
        return f"Here are some helpful tips for DevSphere attendees:\n\n{get_local_tips()}"

    return (
        f"Here's what I can help with:\n\n"
        f"✈️ **Flight status:**\n{check_flight_status(query)}\n\n"
        f"🚗 **Transit options:**\n{get_transit_options(query)}\n\n"
        f"🏨 **Hotels nearby:**\n{search_hotels(query)}\n\n"
        f"🍽️ **Restaurants nearby:**\n{search_restaurants(query)}\n\n"
        f"💡 **Local tips:**\n{get_local_tips()}"
    )


class TravelAgentExecution(AgentExecution):
    """Handles incoming A2A messages and returns travel & logistics info."""

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
        name="Travel & Logistics Agent",
        description="Connects to flight APIs, local transit, and hotel systems. Handles travel logistics and receipt extraction for expense reporting.",
        url="http://localhost:9000",
        version="1.0.0",
        capabilities=AgentCapabilities(streaming=False, pushNotifications=False),
        skills=[
            AgentSkill(
                id="flight-status",
                name="Flight Status",
                description="Check flight status, delays, and gate information.",
                tags=["flights", "delays", "gates", "arrivals"],
                examples=[
                    "What's the status of flight UA 1742?",
                    "Is my flight from SFO delayed?",
                ],
            ),
            AgentSkill(
                id="transit-routes",
                name="Transit Routes",
                description="Get transit options from airport to venue with real-time disruption info.",
                tags=["transit", "airport", "rideshare", "train", "taxi"],
                examples=[
                    "How do I get from the airport to the venue quickly?",
                    "What's the fastest way to the convention center?",
                ],
            ),
            AgentSkill(
                id="hotel-search",
                name="Hotel Search",
                description="Search nearby hotels with availability and pricing.",
                tags=["hotels", "accommodation", "booking"],
                examples=[
                    "What hotels are near the convention center?",
                    "Find me a cheap hotel close to the venue.",
                ],
            ),
            AgentSkill(
                id="receipt-extraction",
                name="Receipt Extraction",
                description="Extract fare details from receipt descriptions for expense reporting.",
                tags=["receipts", "expenses", "reimbursement"],
                examples=[
                    "Log my taxi receipt for $42.50",
                    "Extract my rideshare fare details",
                ],
            ),
            AgentSkill(
                id="restaurant-search",
                name="Restaurant Search",
                description="Find restaurants near the convention center by cuisine, price, or dietary preference.",
                tags=["food", "restaurants", "dining"],
                examples=[
                    "Where should I eat near the convention center?",
                    "Any good Japanese restaurants nearby?",
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

    print("✈️  Travel & Logistics Agent starting on http://localhost:9000")
    print("\U0001f4cb Agent Card: http://localhost:9000/.well-known/agent-card.json")
    uvicorn.run(app.build(), host="0.0.0.0", port=9000)


if __name__ == "__main__":
    main()
