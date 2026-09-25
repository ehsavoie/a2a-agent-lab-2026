package dev.devconf.travel;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class TravelTool {

    record Flight(String flight, String origin, String destination,
                  String scheduledArrival, String actualArrival,
                  int delayMinutes, String status, String gate,
                  String terminal, String note) {}

    record TransitOption(String mode, int durationMinutes, String cost,
                         String availability, String disruption,
                         String recommendation) {}

    record Hotel(String name, String distance, String pricePerNight,
                 String availability, double rating, String amenities) {}

    record Restaurant(String name, String cuisine, String distance,
                      String price, double rating, String description) {}

    private static final List<Flight> FLIGHTS = List.of(
            new Flight("UA 998", "EWR", "BRU", "06:30", "08:35", 125, "DELAYED",
                    "B44", "Terminal A", "Delayed due to late-arriving aircraft from Chicago."),
            new Flight("LH 1014", "FRA", "BRU", "07:45", "07:40", 0, "ON TIME",
                    "A22", "Terminal A", "Arrived early."),
            new Flight("BA 392", "LHR", "BRU", "08:15", "09:00", 45, "DELAYED",
                    "A38", "Terminal A", "Delay due to fog at Heathrow."),
            new Flight("AF 3234", "CDG", "BRU", "07:00", "07:00", 0, "ON TIME",
                    "B12", "Terminal A", ""),
            new Flight("SN 3784", "VIE", "BRU", "09:10", "09:15", 5, "ON TIME",
                    "A16", "Terminal A", "Brussels Airlines. Minor taxi delay on arrival."),
            new Flight("DL 80", "JFK", "BRU", "07:15", "07:50", 35, "DELAYED",
                    "B28", "Terminal A", "Headwind over the Atlantic caused slight delay."),
            new Flight("TK 1937", "IST", "BRU", "10:00", "10:00", 0, "ON TIME",
                    "B06", "Terminal A", "Turkish Airlines direct flight.")
    );

    private static final List<TransitOption> TRANSIT_OPTIONS = List.of(
            new TransitOption("Rideshare (Bolt/Uber)", 35, "€45",
                    "Available now", null,
                    "Direct to Kinepolis Antwerp. Pickup at Brussels Airport arrivals level."),
            new TransitOption("Train (Brussels Airport → Antwerp-Centraal)", 35, "€12",
                    "Every 15 min (NMBS/SNCB)",
                    "⚠️ Track works between Mechelen and Antwerp — trains diverted via Lier, estimated 55 min today.",
                    "Cheapest option but currently disrupted. Take the train to Antwerp-Centraal, then tram 2 or 6 to Kinepolis."),
            new TransitOption("Taxi (fixed fare)", 40, "€65–75",
                    "Rank outside arrivals hall", null,
                    "Fixed fare to Antwerp. Official Brussels Airport taxis only — avoid touts."),
            new TransitOption("Rental car", 40, "€55/day",
                    "Europcar, Avis, Hertz at arrivals level -1", null,
                    "Take the E19 motorway to Antwerp (45 km). Free parking at Kinepolis.")
    );

    private static final List<Hotel> HOTELS = List.of(
            new Hotel("Hotel Franq", "6 km from Kinepolis (Antwerp center)", "€250",
                    "2 rooms left", 4.8, "Wi-Fi, Michelin-star restaurant, rooftop terrace, boutique rooms"),
            new Hotel("Holiday Inn Express Antwerp City-North", "2 km from Kinepolis", "€99",
                    "Available", 4.2, "Wi-Fi, breakfast included, near Ring road, free parking"),
            new Hotel("Hotel Lindner Antwerp", "5 km from Kinepolis (city center)", "€140",
                    "Available", 4.5, "Wi-Fi, fitness center, near Antwerp-Centraal station, bar"),
            new Hotel("B&B Hotel Antwerp Centre", "5 km from Kinepolis", "€75",
                    "Available", 4.0, "Wi-Fi, central location, budget-friendly, near Meir shopping street"),
            new Hotel("Van der Valk Hotel Antwerpen", "3 km from Kinepolis", "€120",
                    "Limited — 1 room", 4.3, "Wi-Fi, pool, sauna, free parking, restaurant, near E19")
    );

    private static final List<Restaurant> RESTAURANTS = List.of(
            new Restaurant("Frites Atelier", "Belgian / Frites",
                    "5 km (Antwerp center, Korte Gasthuisstraat)", "€", 4.6,
                    "Sergio Herman's gourmet frites concept. A must-try Belgian classic with premium toppings."),
            new Restaurant("The Jane", "Modern European",
                    "4 km (Antwerp, chapel setting)", "€€€€", 4.9,
                    "Michelin two-star restaurant in a converted chapel. Book well in advance for the upper room; the bar downstairs is walk-in."),
            new Restaurant("Balls & Glory", "Belgian comfort food",
                    "5 km (Antwerp center, Nationalestraat)", "€€", 4.4,
                    "Signature Belgian stew balls (stoofvlees, curry, veggie) with mashed potatoes. Quick and hearty."),
            new Restaurant("Elfde Gebod", "Traditional Belgian",
                    "5 km (Antwerp, Torfbrug near Cathedral)", "€€", 4.3,
                    "Classic Belgian brasserie next to the Cathedral. Mussels, stoofvlees, and Belgian beers in a quirky church-themed interior."),
            new Restaurant("Umamido", "Japanese Ramen",
                    "5 km (Antwerp center, Paardemarkt)", "€€", 4.5,
                    "Authentic tonkotsu ramen — a warm bowl between conference sessions. Quick service, no reservations needed."),
            new Restaurant("Kaffeenini", "Café / Brunch",
                    "5 km (Antwerp, Lange Leemstraat)", "€", 4.7,
                    "Specialty coffee, avocado toast, and homemade pastries. Popular with locals for weekend brunch.")
    );

    private static final List<String> LOCAL_TIPS = List.of(
            "Devoxx Belgium is at Kinepolis Antwerp (Groenendaallaan 394, 2030 Antwerp) — it's a cinema complex, not the city center.",
            "Belgian train tickets (NMBS/SNCB) are cheaper when bought via the app. A Brussels Airport supplement (€6.40) applies to all departing trains.",
            "Antwerp-Centraal is one of the most beautiful train stations in the world — worth arriving early to admire the architecture.",
            "Belgian beers to try: Duvel, Westmalle Tripel, Orval, De Koninck (Antwerp's local pilsner — locals call it a 'bolleke').",
            "Tipping in Belgium is not expected — service is included. Round up for good service.",
            "Kinepolis has free parking. If you're staying in Antwerp center, tram 2 or 6 goes directly to the venue.",
            "For a scenic walk, stroll along the Scheldt river quays (Het Eilandje) — great views of the port and MAS museum.",
            "The Meir is Antwerp's main shopping street, connecting Antwerp-Centraal station to the Groenplaats and Cathedral area."
    );

    @Tool("Check flight status for flights arriving at Brussels Airport (BRU). Can search by flight number or origin airport code.")
    public String checkFlightStatus(String query) {
        String q = query.toUpperCase().replace(" ", "");
        List<Flight> matches = FLIGHTS.stream()
                .filter(f -> q.contains(f.flight().replace(" ", "")) || q.contains(f.origin()))
                .toList();

        if (matches.isEmpty()) {
            matches = FLIGHTS;
        }

        StringBuilder sb = new StringBuilder();
        for (Flight f : matches) {
            String icon = "ON TIME".equals(f.status()) ? "✅" : "⚠️";
            String delay = f.delayMinutes() > 0 ? " (delayed " + f.delayMinutes() + " min)" : "";
            sb.append(icon).append(" **").append(f.flight()).append("** (")
                    .append(f.origin()).append(" → ").append(f.destination()).append(")\n")
                    .append("  Scheduled: ").append(f.scheduledArrival())
                    .append(" | Actual: ").append(f.actualArrival()).append(delay).append("\n")
                    .append("  Status: ").append(f.status())
                    .append(" | Gate: ").append(f.gate())
                    .append(" | ").append(f.terminal());
            if (!f.note().isEmpty()) {
                sb.append("\n  Note: ").append(f.note());
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    @Tool("Get transit options from Brussels Airport (BRU) to Kinepolis Antwerp, including real-time disruptions.")
    public String getTransitOptions() {
        StringBuilder sb = new StringBuilder("**Transit options from Brussels Airport (BRU) to Kinepolis Antwerp:**\n\n");
        String bestOption = null;
        int bestTime = 999;

        for (TransitOption opt : TRANSIT_OPTIONS) {
            int effectiveTime = opt.durationMinutes();
            if (opt.disruption() != null && opt.disruption().contains("55 min")) {
                effectiveTime = 55;
            }
            if (effectiveTime < bestTime) {
                bestTime = effectiveTime;
                bestOption = opt.mode();
            }

            sb.append("🚗 **").append(opt.mode()).append("** — ").append(opt.cost())
                    .append(" — ~").append(effectiveTime).append(" min\n")
                    .append("  ").append(opt.recommendation());
            if (opt.disruption() != null) {
                sb.append("\n  ").append(opt.disruption());
            }
            sb.append("\n\n");
        }
        sb.append("💡 **Recommendation:** ").append(bestOption)
                .append(" is the fastest option today (~").append(bestTime).append(" min).\n\n");
        sb.append("🚊 Tram to Kinepolis: From Antwerp-Centraal: Tram 2 (direction Hoboken) or Tram 6 (direction Luchtbal), stop Groenendaallaan — 5 min walk to Kinepolis.\n\n");
        sb.append("🅿️ Parking: Kinepolis Antwerp has a large free parking lot. Address: Groenendaallaan 394, 2030 Antwerp.");
        return sb.toString();
    }

    @Tool("Search hotels near Kinepolis Antwerp. Can filter by 'cheap' or 'luxury'.")
    public String searchHotels(String query) {
        String q = query.toLowerCase();
        List<Hotel> matches;
        if (q.contains("cheap") || q.contains("budget")) {
            matches = HOTELS.stream().filter(h -> {
                double price = Double.parseDouble(h.pricePerNight().replaceAll("[^\\d.]", ""));
                return price <= 100;
            }).toList();
        } else if (q.contains("luxury") || q.contains("premium")) {
            matches = HOTELS.stream().filter(h -> h.rating() >= 4.7).toList();
        } else {
            matches = HOTELS;
        }

        StringBuilder sb = new StringBuilder();
        for (Hotel h : matches) {
            String icon = h.availability().startsWith("Available") ? "🟢" : "🟡";
            sb.append(icon).append(" **").append(h.name()).append("** — ")
                    .append(h.pricePerNight()).append("/night — ⭐ ").append(h.rating()).append("\n")
                    .append("  📍 ").append(h.distance()).append(" | ").append(h.availability()).append("\n")
                    .append("  Amenities: ").append(h.amenities()).append("\n\n");
        }
        return sb.toString();
    }

    @Tool("Search restaurants in Antwerp near the conference venue.")
    public String searchRestaurants(String query) {
        String q = query.toLowerCase();
        List<Restaurant> matches = RESTAURANTS.stream()
                .filter(r -> r.cuisine().toLowerCase().contains(q)
                        || r.name().toLowerCase().contains(q)
                        || r.description().toLowerCase().contains(q))
                .toList();

        if (matches.isEmpty()) {
            matches = RESTAURANTS;
        }

        StringBuilder sb = new StringBuilder();
        for (Restaurant r : matches) {
            sb.append("**").append(r.name()).append("** (").append(r.cuisine()).append(") — ")
                    .append(r.price()).append(" — ⭐ ").append(r.rating()).append("\n")
                    .append("  📍 ").append(r.distance()).append("\n")
                    .append("  ").append(r.description()).append("\n\n");
        }
        return sb.toString();
    }

    @Tool("Extract receipt details from a description for expense reporting. Provide the receipt description including vendor, amount, and type of expense.")
    public String extractReceipt(String description) {
        String q = description.toLowerCase();

        String vendor = "Unknown vendor";
        String amount = "0.00";
        String currency = "EUR";
        String category = "transportation";
        String date = LocalDate.now().toString();

        if (q.contains("taxi") || q.contains("cab")) {
            vendor = "Antwerp Taxi Service";
            amount = "65.00";
            category = "ground_transportation";
        } else if (q.contains("uber") || q.contains("bolt") || q.contains("rideshare") || q.contains("ride")) {
            vendor = "Bolt Belgium";
            amount = "45.00";
            category = "ground_transportation";
        } else if (q.contains("train") || q.contains("tram") || q.contains("rail") || q.contains("nmbs") || q.contains("sncb")) {
            vendor = "NMBS/SNCB Belgian Railways";
            amount = "12.00";
            category = "public_transit";
        } else if (q.contains("hotel") || q.contains("room")) {
            vendor = "Holiday Inn Express Antwerp";
            amount = "99.00";
            category = "lodging";
        } else if (q.contains("food") || q.contains("restaurant") || q.contains("meal") || q.contains("dinner") || q.contains("lunch")) {
            vendor = "Balls & Glory Antwerp";
            amount = "18.50";
            category = "meals";
        }

        for (String word : q.split("\\s+")) {
            String clean = word.replaceAll("[€$£,.]$", "");
            clean = clean.replaceAll("^[€$£]", "");
            try {
                double parsed = Double.parseDouble(clean);
                amount = String.format("%.2f", parsed);
                break;
            } catch (NumberFormatException ignored) {
            }
        }

        String confidence = "Unknown vendor".equals(vendor) ? "low" : "high";

        return String.format("""
                📄 **Receipt extracted:**

                ```
                Vendor:   %s
                Amount:   %s %s
                Date:     %s
                Category: %s
                Status:   pending_review
                ```

                This receipt data is ready to be forwarded to the Expense & Compliance Agent \
                for audit-ready processing.

                _Structured payload:_
                ```json
                {
                  "vendor": "%s",
                  "amount": "%s",
                  "currency": "%s",
                  "date": "%s",
                  "category": "%s",
                  "status": "pending_review",
                  "confidence": "%s"
                }
                ```""", vendor, currency, amount, date, category,
                vendor, amount, currency, date, category, confidence);
    }

    @Tool("Get helpful local tips for Devoxx Belgium attendees about the venue, Belgian culture, transport, and dining.")
    public String getLocalTips() {
        StringBuilder sb = new StringBuilder();
        for (String tip : LOCAL_TIPS) {
            sb.append("💡 ").append(tip).append("\n");
        }
        return sb.toString();
    }
}
