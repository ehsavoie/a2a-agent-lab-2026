package dev.devconf.venue;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class VenueTool {

    private Map<String, RoomInfo> rooms;
    private Map<String, CateringStation> cateringStations;
    private Map<String, String> directions;

    public record RoomInfo(String name, int capacity, int occupancyPercent) {
        public int available() {
            return capacity - (capacity * occupancyPercent / 100);
        }
    }

    public record CateringStation(String name, String location, int queueLength, int estimatedWaitMinutes) {}

    @PostConstruct
    void loadVenueData() {
        rooms = new LinkedHashMap<>();
        rooms.put("main hall a", new RoomInfo("Main Hall A", 2000, 85));
        rooms.put("hall b", new RoomInfo("Hall B", 500, 60));
        rooms.put("room 201", new RoomInfo("Room 201", 150, 72));
        rooms.put("room 301", new RoomInfo("Room 301", 200, 45));
        rooms.put("room 401", new RoomInfo("Room 401", 120, 90));
        rooms.put("room 102", new RoomInfo("Room 102", 180, 55));
        rooms.put("lab room b", new RoomInfo("Lab Room B", 60, 33));

        cateringStations = new LinkedHashMap<>();
        cateringStations.put("main lobby cafe", new CateringStation("Main Lobby Cafe", "Ground Floor, near Main Hall A entrance", 12, 8));
        cateringStations.put("east wing coffee", new CateringStation("East Wing Coffee Bar", "2nd Floor, between Room 201 and Room 301", 3, 2));
        cateringStations.put("hall b snack bar", new CateringStation("Hall B Snack Bar", "Hall B foyer", 7, 5));
        cateringStations.put("rooftop terrace", new CateringStation("Rooftop Terrace Restaurant", "5th Floor", 0, 0));

        directions = new LinkedHashMap<>();
        directions.put("entrance->main hall a", "From the main entrance, walk straight through the lobby (50m). Main Hall A is directly ahead through the double doors.");
        directions.put("entrance->hall b", "From the main entrance, turn right past the registration desk. Follow signs to Hall B (120m). Take the escalator down one level.");
        directions.put("entrance->room 201", "From the main entrance, take the elevator or stairs to the 2nd floor. Room 201 is to the left, 30m down the corridor.");
        directions.put("entrance->room 301", "From the main entrance, take the elevator or stairs to the 3rd floor. Room 301 is at the end of the main corridor.");
        directions.put("entrance->room 401", "From the main entrance, take the elevator to the 4th floor. Room 401 is immediately to your right.");
        directions.put("entrance->room 102", "From the main entrance, turn left and walk 80m. Room 102 is on the ground floor, past the sponsor exhibition area.");
        directions.put("entrance->lab room b", "From the main entrance, take the elevator to the 2nd floor, turn right, and follow signs to Lab Room B (end of east corridor).");
        directions.put("main hall a->hall b", "Exit Main Hall A through the rear doors, take the escalator down one level, and follow signs to Hall B (200m total).");
        directions.put("hall b->room 201", "From Hall B, take the escalator up two levels to the 2nd floor. Room 201 is 50m to the left.");
        directions.put("room 201->room 301", "Take the stairs or elevator one floor up. Room 301 is in the same wing, directly above Room 201.");
    }

    @Tool("Check real-time room capacity and occupancy from IoT sensors. Provide the room name.")
    public String checkRoomCapacity(@P("The room name to check") String roomName) {
        String key = roomName.toLowerCase().trim();
        RoomInfo room = rooms.get(key);
        if (room == null) {
            for (Map.Entry<String, RoomInfo> entry : rooms.entrySet()) {
                if (entry.getKey().contains(key) || key.contains(entry.getKey())) {
                    room = entry.getValue();
                    break;
                }
            }
        }
        if (room == null) {
            return "Room '" + roomName + "' not found. Available rooms: "
                    + rooms.values().stream().map(RoomInfo::name).collect(Collectors.joining(", "));
        }
        String status = room.occupancyPercent() >= 95 ? "FULL" :
                         room.occupancyPercent() >= 80 ? "Nearly Full" : "Available";
        return String.format("**%s** (IoT Sensor Reading)\n"
                + "- Total Capacity: %d seats\n"
                + "- Current Occupancy: %d%% (%d/%d seats occupied)\n"
                + "- Available Seats: %d\n"
                + "- Status: %s",
                room.name(), room.capacity(), room.occupancyPercent(),
                room.capacity() - room.available(), room.capacity(),
                room.available(), status);
    }

    @Tool("Get indoor walking directions between two locations in the venue.")
    public String getIndoorDirections(
            @P("Starting location") String from,
            @P("Destination location") String to) {
        String key = from.toLowerCase().trim() + "->" + to.toLowerCase().trim();
        String result = directions.get(key);
        if (result != null) {
            return "**Directions: " + from + " -> " + to + "**\n" + result;
        }
        String reverseKey = to.toLowerCase().trim() + "->" + from.toLowerCase().trim();
        result = directions.get(reverseKey);
        if (result != null) {
            return "**Directions: " + from + " -> " + to + "** (reverse route)\n"
                    + result + "\n(Follow this route in reverse.)";
        }
        return "No specific directions found from '" + from + "' to '" + to + "'. "
                + "Please ask the information desk in the main lobby for assistance. "
                + "Available landmarks: Main Entrance, Main Hall A, Hall B, Room 201, Room 301, Room 401, Room 102, Lab Room B.";
    }

    @Tool("Get current catering and food station queue information across the venue.")
    public String getCateringStatus() {
        StringBuilder sb = new StringBuilder("**Catering Station Status** (Real-Time IoT Queue Sensors)\n\n");
        for (CateringStation station : cateringStations.values()) {
            String queueStatus = station.queueLength() == 0 ? "No queue" :
                                 station.queueLength() <= 5 ? "Short queue" : "Busy";
            sb.append(String.format("- **%s** (%s)\n  Queue: %d people | Wait: ~%d min | Status: %s\n\n",
                    station.name(), station.location(),
                    station.queueLength(), station.estimatedWaitMinutes(), queueStatus));
        }
        return sb.toString();
    }

    @Tool("Reserve a fast-track entry pass for an attendee at a specific room.")
    public String reserveEntryPass(
            @P("The attendee's name") String attendeeName,
            @P("The room name") String roomName) {
        String key = roomName.toLowerCase().trim();
        RoomInfo room = rooms.get(key);
        if (room == null) {
            for (Map.Entry<String, RoomInfo> entry : rooms.entrySet()) {
                if (entry.getKey().contains(key) || key.contains(entry.getKey())) {
                    room = entry.getValue();
                    break;
                }
            }
        }
        if (room == null) {
            return "Cannot reserve pass: room '" + roomName + "' not found.";
        }
        if (room.occupancyPercent() >= 95) {
            return "Cannot reserve pass: **" + room.name() + "** is currently at full capacity (" + room.occupancyPercent() + "%). "
                    + "Please try again later or choose an alternative session.";
        }
        String passId = "FTP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return String.format("**Fast-Track Entry Pass Confirmed**\n"
                + "- Pass ID: %s\n"
                + "- Attendee: %s\n"
                + "- Room: %s\n"
                + "- Current Occupancy: %d%%\n"
                + "- Instructions: Show this pass ID at the room entrance for priority seating.\n"
                + "- Valid for: 30 minutes from now",
                passId, attendeeName, room.name(), room.occupancyPercent());
    }
}
