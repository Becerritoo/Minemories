package dev.becerritoo.minemories.paper;

interface DiscordDelivery {
    Result send(Settings settings, TicketService.PhotoTicket ticket,
                ImageSanitizer.SanitizedImage image, String caption,
                boolean includeCoordinates);

    String description();

    record Result(boolean success, String reason) {
    }
}
