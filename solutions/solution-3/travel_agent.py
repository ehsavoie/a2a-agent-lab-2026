"""
DevSphere 2026 Travel & Logistics Agent — A2A Python Agent

Connects to flight APIs, local transit, and hotel systems.
Handles travel logistics, transit comparison, and receipt extraction
for expense reporting.
"""

import json
import uuid
from datetime import datetime

from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.routes import create_rest_routes, create_agent_card_routes
from a2a.server.tasks import InMemoryTaskStore
from a2a.types.a2a_pb2 import (
    AgentCapabilities,
    AgentCard,
    AgentInterface,
    AgentSkill,
    Message,
    Part,
    Role,
)
from starlette.applications import Starlette
import uvicorn


TRAVEL_DATA = {
    "flights": [
        {
            "flight": "UA 998",
            "origin": "EWR",
            "destination": "BRU",
            "scheduled_arrival": "06:30",
            "actual_arrival": "08:35",
            "delay_minutes": 125,
            "status": "DELAYED",
            "gate": "B44",
            "terminal": "Terminal A",
            "note": "Delayed due to late-arriving aircraft from Chicago.",
        },
        {
            "flight": "LH 1014",
            "origin": "FRA",
            "destination": "BRU",
            "scheduled_arrival": "07:45",
            "actual_arrival": "07:40",
            "delay_minutes": 0,
            "status": "ON TIME",
            "gate": "A22",
            "terminal": "Terminal A",
            "note": "Arrived early.",
        },
        {
            "flight": "BA 392",
            "origin": "LHR",
            "destination": "BRU",
            "scheduled_arrival": "08:15",
            "actual_arrival": "09:00",
            "delay_minutes": 45,
            "status": "DELAYED",
            "gate": "A38",
            "terminal": "Terminal A",
            "note": "Delay due to fog at Heathrow.",
        },
        {
            "flight": "AF 3234",
            "origin": "CDG",
            "destination": "BRU",
            "scheduled_arrival": "07:00",
            "actual_arrival": "07:00",
            "delay_minutes": 0,
            "status": "ON TIME",
            "gate": "B12",
            "terminal": "Terminal A",
            "note": "",
        },
        {
            "flight": "SN 3784",
            "origin": "VIE",
            "destination": "BRU",
            "scheduled_arrival": "09:10",
            "actual_arrival": "09:15",
            "delay_minutes": 5,
            "status": "ON TIME",
            "gate": "A16",
            "terminal": "Terminal A",
            "note": "Brussels Airlines. Minor taxi delay on arrival.",
        },
        {
            "flight": "DL 80",
            "origin": "JFK",
            "destination": "BRU",
            "scheduled_arrival": "07:15",
            "actual_arrival": "07:50",
            "delay_minutes": 35,
            "status": "DELAYED",
            "gate": "B28",
            "terminal": "Terminal A",
            "note": "Headwind over the Atlantic caused slight delay.",
        },
        {
            "flight": "TK 1937",
            "origin": "IST",
            "destination": "BRU",
            "scheduled_arrival": "10:00",
            "actual_arrival": "10:00",
            "delay_minutes": 0,
            "status": "ON TIME",
            "gate": "B06",
            "terminal": "Terminal A",
            "note": "Turkish Airlines direct flight.",
        },
    ],
    "transit": {
        "options": [
            {
                "mode": "Rideshare (Bolt/Uber)",
                "duration_minutes": 35,
                "cost": "€45",
                "availability": "Available now",
                "disruption": None,
                "recommendation": "Direct to Kinepolis Antwerp. Pickup at Brussels Airport arrivals level.",
            },
            {
                "mode": "Train (Brussels Airport → Antwerp-Centraal)",
                "duration_minutes": 35,
                "cost": "€12",
                "availability": "Every 15 min (NMBS/SNCB)",
                "disruption": "⚠️ Track works between Mechelen and Antwerp — trains diverted via Lier, estimated 55 min today.",
                "recommendation": "Cheapest option but currently disrupted. Take the train to Antwerp-Centraal, then tram 2 or 6 to Kinepolis.",
            },
            {
                "mode": "Taxi (fixed fare)",
                "duration_minutes": 40,
                "cost": "€65–75",
                "availability": "Rank outside arrivals hall",
                "disruption": None,
                "recommendation": "Fixed fare to Antwerp. Official Brussels Airport taxis only — avoid touts.",
            },
            {
                "mode": "Rental car",
                "duration_minutes": 40,
                "cost": "€55/day",
                "availability": "Europcar, Avis, Hertz at arrivals level -1",
                "disruption": None,
                "recommendation": "Take the E19 motorway to Antwerp (45 km). Free parking at Kinepolis.",
            },
        ],
        "tram_to_kinepolis": "From Antwerp-Centraal: Tram 2 (direction Hoboken) or Tram 6 (direction Luchtbal), stop Groenendaallaan — 5 min walk to Kinepolis.",
        "parking": "Kinepolis Antwerp has a large free parking lot. Address: Groenendaallaan 394, 2030 Antwerp.",
    },
    "hotels": [
        {
            "name": "Hotel Franq",
            "distance": "6 km from Kinepolis (Antwerp center)",
            "price_per_night": "€250",
            "availability": "2 rooms left",
            "rating": 4.8,
            "amenities": "Wi-Fi, Michelin-star restaurant, rooftop terrace, boutique rooms",
        },
        {
            "name": "Holiday Inn Express Antwerp City-North",
            "distance": "2 km from Kinepolis",
            "price_per_night": "€99",
            "availability": "Available",
            "rating": 4.2,
            "amenities": "Wi-Fi, breakfast included, near Ring road, free parking",
        },
        {
            "name": "Hotel Lindner Antwerp",
            "distance": "5 km from Kinepolis (city center)",
            "price_per_night": "€140",
            "availability": "Available",
            "rating": 4.5,
            "amenities": "Wi-Fi, fitness center, near Antwerp-Centraal station, bar",
        },
        {
            "name": "B&B Hotel Antwerp Centre",
            "distance": "5 km from Kinepolis",
            "price_per_night": "€75",
            "availability": "Available",
            "rating": 4.0,
            "amenities": "Wi-Fi, central location, budget-friendly, near Meir shopping street",
        },
        {
            "name": "Van der Valk Hotel Antwerpen",
            "distance": "3 km from Kinepolis",
            "price_per_night": "€120",
            "availability": "Limited — 1 room",
            "rating": 4.3,
            "amenities": "Wi-Fi, pool, sauna, free parking, restaurant, near E19",
        },
    ],
    "restaurants": [
        {
            "name": "Frites Atelier",
            "cuisine": "Belgian / Frites",
            "distance": "5 km (Antwerp center, Korte Gasthuisstraat)",
            "price": "€",
            "rating": 4.6,
            "description": "Sergio Herman's gourmet frites concept. A must-try Belgian classic with premium toppings.",
        },
        {
            "name": "The Jane",
            "cuisine": "Modern European",
            "distance": "4 km (Antwerp, chapel setting)",
            "price": "€€€€",
            "rating": 4.9,
            "description": "Michelin two-star restaurant in a converted chapel. Book well in advance for the upper room; the bar downstairs is walk-in.",
        },
        {
            "name": "Balls & Glory",
            "cuisine": "Belgian comfort food",
            "distance": "5 km (Antwerp center, Nationalestraat)",
            "price": "€€",
            "rating": 4.4,
            "description": "Signature Belgian stew balls (stoofvlees, curry, veggie) with mashed potatoes. Quick and hearty.",
        },
        {
            "name": "Elfde Gebod",
            "cuisine": "Traditional Belgian",
            "distance": "5 km (Antwerp, Torfbrug near Cathedral)",
            "price": "€€",
            "rating": 4.3,
            "description": "Classic Belgian brasserie next to the Cathedral. Mussels, stoofvlees, and Belgian beers in a quirky church-themed interior.",
        },
        {
            "name": "Umamido",
            "cuisine": "Japanese Ramen",
            "distance": "5 km (Antwerp center, Paardemarkt)",
            "price": "€€",
            "rating": 4.5,
            "description": "Authentic tonkotsu ramen — a warm bowl between conference sessions. Quick service, no reservations needed.",
        },
        {
            "name": "Kaffeenini",
            "cuisine": "Café / Brunch",
            "distance": "5 km (Antwerp, Lange Leemstraat)",
            "price": "€",
            "rating": 4.7,
            "description": "Specialty coffee, avocado toast, and homemade pastries. Popular with locals for weekend brunch.",
        },
    ],
    "local_tips": [
        "Devoxx Belgium is at Kinepolis Antwerp (Groenendaallaan 394, 2030 Antwerp) — it's a cinema complex, not the city center.",
        "Belgian train tickets (NMBS/SNCB) are cheaper when bought via the app. A Brussels Airport supplement (€6.40) applies to all departing trains.",
        "Antwerp-Centraal is one of the most beautiful train stations in the world — worth arriving early to admire the architecture.",
        "Belgian beers to try: Duvel, Westmalle Tripel, Orval, De Koninck (Antwerp's local pilsner — locals call it a 'bolleke').",
        "Tipping in Belgium is not expected — service is included. Round up for good service.",
        "Kinepolis has free parking. If you're staying in Antwerp center, tram 2 or 6 goes directly to the venue.",
        "For a scenic walk, stroll along the Scheldt river quays (Het Eilandje) — great views of the port and MAS museum.",
        "The Meir is Antwerp's main shopping street, connecting Antwerp-Centraal station to the Groenplaats and Cathedral area.",
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
    lines = ["**Transit options from Brussels Airport (BRU) to Kinepolis Antwerp:**\n"]
    best_option = None
    best_time = 999

    for opt in TRAVEL_DATA["transit"]["options"]:
        effective_time = opt["duration_minutes"]
        disruption_note = ""
        if opt["disruption"]:
            disruption_note = f"\n  {opt['disruption']}"
            if "55 min" in opt["disruption"]:
                effective_time = 55

        if effective_time < best_time:
            best_time = effective_time
            best_option = opt["mode"]

        lines.append(
            f"🚗 **{opt['mode']}** — {opt['cost']} — ~{effective_time} min\n"
            f"  {opt['recommendation']}{disruption_note}"
        )

    lines.append(f"\n💡 **Recommendation:** {best_option} is the fastest option today (~{best_time} min).")

    if any(w in query.lower() for w in ["tram", "station", "walk", "kinepolis"]):
        lines.append(f"\n🚊 Tram to Kinepolis: {TRAVEL_DATA['transit']['tram_to_kinepolis']}")

    return "\n\n".join(lines)


def search_hotels(query: str) -> str:
    query_lower = query.lower()
    matches = []
    if "cheap" in query_lower:
        matches = [
            h for h in TRAVEL_DATA["hotels"]
            if float(h["price_per_night"].lstrip("€$")) <= 100
        ]
    elif "luxury" in query_lower:
        matches = [h for h in TRAVEL_DATA["hotels"] if h["rating"] >= 4.7]
    else:
        search_terms = [
            word for word in query_lower.split()
            if word not in {"a", "an", "the", "find", "me", "hotel", "hotels", "near", "close", "to", "venue"}
        ]
        matches = [
            h for h in TRAVEL_DATA["hotels"]
            if any(term in h["name"].lower() for term in search_terms)
        ]

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
    currency = "EUR"
    category = "transportation"
    date = datetime.now().strftime("%Y-%m-%d")

    if any(w in query_lower for w in ["taxi", "cab"]):
        vendor = "Antwerp Taxi Service"
        amount = "65.00"
        category = "ground_transportation"
    elif any(w in query_lower for w in ["uber", "lyft", "bolt", "rideshare", "ride"]):
        vendor = "Bolt Belgium"
        amount = "45.00"
        category = "ground_transportation"
    elif any(w in query_lower for w in ["train", "tram", "rail", "nmbs", "sncb"]):
        vendor = "NMBS/SNCB Belgian Railways"
        amount = "12.00"
        category = "public_transit"
    elif any(w in query_lower for w in ["hotel", "room"]):
        vendor = "Holiday Inn Express Antwerp"
        amount = "99.00"
        category = "lodging"
    elif any(w in query_lower for w in ["food", "restaurant", "meal", "dinner", "lunch"]):
        vendor = "Balls & Glory Antwerp"
        amount = "18.50"
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

    asking_for_directions = any(
        phrase in query_lower
        for phrase in ["how do i get", "how can i get", "how do we get", "how can she get",
                       "quickest way to", "fastest way to", "directions to"]
    )
    mentions_destination = any(
        place in query_lower
        for place in ["airport", "venue", "kinepolis", "antwerp", "station", "tram", "convention center"]
    )
    if asking_for_directions and mentions_destination:
        return f"Here are your transit options:\n\n{get_transit_options(query)}"

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
        return f"Here are restaurant recommendations in Antwerp:\n\n{search_restaurants(query)}"

    if any(w in query_lower for w in ["tip", "advice", "know", "helpful", "beer", "belgian"]):
        return f"Here are some helpful tips for DevSphere attendees:\n\n{get_local_tips()}"

    return (
        f"Here's what I can help with:\n\n"
        f"✈️ **Flight status:**\n{check_flight_status(query)}\n\n"
        f"🚗 **Transit options:**\n{get_transit_options(query)}\n\n"
        f"🏨 **Hotels nearby:**\n{search_hotels(query)}\n\n"
        f"🍽️ **Restaurants in Antwerp:**\n{search_restaurants(query)}\n\n"
        f"💡 **Local tips:**\n{get_local_tips()}"
    )


class TravelAgentExecutor(AgentExecutor):
    """Handles incoming A2A messages and returns travel & logistics info."""

    async def execute(self, context: RequestContext, event_queue: EventQueue) -> None:
        user_message = context.get_user_input()
        if not user_message:
            user_message = "general"

        response_text = answer_query(user_message)

        await event_queue.enqueue_event(
            Message(
                role=Role.ROLE_AGENT,
                parts=[Part(text=response_text)],
                message_id=str(uuid.uuid4()),
            )
        )

    async def cancel(self, context: RequestContext, event_queue: EventQueue) -> None:
        pass


def build_agent_card() -> AgentCard:
    return AgentCard(
        name="Travel & Logistics Agent",
        description="Handles travel logistics for Devoxx Belgium at Kinepolis Antwerp: flights to Brussels Airport, transit to Antwerp, hotels, restaurants, and receipt extraction for expense reporting.",
        supported_interfaces=[
            AgentInterface(
                url="http://localhost:9000",
                protocol_binding="HTTP+JSON",
                protocol_version="1.0",
            ),
        ],
        version="1.0.0",
        capabilities=AgentCapabilities(streaming=False, push_notifications=False),
        skills=[
            AgentSkill(
                id="flight-status",
                name="Flight Status",
                description="Check flight status, delays, and gate information.",
                tags=["flights", "delays", "gates", "arrivals"],
                examples=[
                    "What's the status of flight UA 998?",
                    "Is my flight to Brussels delayed?",
                ],
            ),
            AgentSkill(
                id="transit-routes",
                name="Transit Routes",
                description="Get transit options from airport to venue with real-time disruption info.",
                tags=["transit", "airport", "rideshare", "train", "taxi"],
                examples=[
                    "How do I get from Brussels Airport to Kinepolis Antwerp?",
                    "What's the fastest way to the venue?",
                ],
            ),
            AgentSkill(
                id="hotel-search",
                name="Hotel Search",
                description="Search nearby hotels with availability and pricing.",
                tags=["hotels", "accommodation", "booking"],
                examples=[
                    "What hotels are near Kinepolis Antwerp?",
                    "Find me a cheap hotel close to the venue.",
                ],
            ),
            AgentSkill(
                id="receipt-extraction",
                name="Receipt Extraction",
                description="Extract fare details from receipt descriptions for expense reporting.",
                tags=["receipts", "expenses", "reimbursement"],
                examples=[
                    "Log my taxi receipt for €65",
                    "Extract my Bolt rideshare fare details",
                ],
            ),
            AgentSkill(
                id="restaurant-search",
                name="Restaurant Search",
                description="Find restaurants near the convention center by cuisine, price, or dietary preference.",
                tags=["food", "restaurants", "dining"],
                examples=[
                    "Where should I eat in Antwerp?",
                    "Any good restaurants near the venue?",
                ],
            ),
            AgentSkill(
                id="local-tips",
                name="Local Tips",
                description="Helpful tips for conference attendees about the venue and surroundings.",
                tags=["tips", "venue", "wifi", "coffee"],
                examples=[
                    "Any tips for Devoxx Belgium first-timers?",
                    "What Belgian beers should I try?",
                ],
            ),
        ],
        default_input_modes=["text"],
        default_output_modes=["text"],
    )


def main():
    agent_card = build_agent_card()
    agent_executor = TravelAgentExecutor()

    request_handler = DefaultRequestHandler(
        agent_executor=agent_executor,
        task_store=InMemoryTaskStore(),
        agent_card=agent_card,
    )

    routes = create_agent_card_routes(agent_card) + create_rest_routes(request_handler)
    app = Starlette(routes=routes)

    print("✈️  Travel & Logistics Agent starting on http://localhost:9000")
    print("\U0001f4cb Agent Card: http://localhost:9000/.well-known/agent-card.json")
    uvicorn.run(app, host="0.0.0.0", port=9000)


if __name__ == "__main__":
    main()
