package dev.becerritoo.minemories.paper;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

final class DiscordSrvBridge implements DiscordDelivery {
    private final boolean detected;
    private final Object accountLinkManager;
    private final Method getDiscordId;
    private final Object targetChannel;
    private final Constructor<?> embedBuilderConstructor;
    private final Method setColor;
    private final Method setAuthor;
    private final Method setDescription;
    private final Method addField;
    private final Method setImage;
    private final Method setFooter;
    private final Method setTimestamp;
    private final Method buildEmbed;
    private final Method sendFile;
    private final Method setEmbeds;
    private final Method allowedMentions;
    private final Method complete;
    private final Class<?> attachmentOptionClass;
    private final Class<?> messageEmbedClass;
    private final String destinationDescription;
    private final String deliveryError;
    private final Logger logger;

    DiscordSrvBridge(PluginManager plugins, Logger logger, String configuredChannelId) {
        this.logger = logger;
        Plugin installed = plugins.getPlugin("DiscordSRV");
        boolean found = installed != null && installed.isEnabled();
        Object links = null;
        Method discordLookup = null;
        Object channel = null;
        Constructor<?> builderConstructor = null;
        Method color = null;
        Method author = null;
        Method description = null;
        Method field = null;
        Method image = null;
        Method footer = null;
        Method timestamp = null;
        Method build = null;
        Method file = null;
        Method embeds = null;
        Method mentions = null;
        Method blockingComplete = null;
        Class<?> attachmentOption = null;
        Class<?> messageEmbed = null;
        String selected = configuredChannelId == null || configuredChannelId.isBlank()
                ? "canal principal" : "canal " + configuredChannelId;
        String error = null;

        if (found) {
            ClassLoader loader = installed.getClass().getClassLoader();
            Object discordSrv = installed;
            try {
                links = discordSrv.getClass().getMethod("getAccountLinkManager").invoke(discordSrv);
                discordLookup = links.getClass().getMethod("getDiscordId", UUID.class);
            } catch (ReflectiveOperationException exception) {
                logger.warning("DiscordSRV está instalado, pero su API de vinculación no es compatible: "
                        + exception.getClass().getSimpleName());
                links = null;
                discordLookup = null;
            }

            try {
                Class<?> jdaClass = loader.loadClass(
                        "github.scarsz.discordsrv.dependencies.jda.api.JDA");
                Class<?> textChannelClass = loader.loadClass(
                        "github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel");
                Class<?> messageActionClass = loader.loadClass(
                        "github.scarsz.discordsrv.dependencies.jda.api.requests.restaction.MessageAction");
                Class<?> restActionClass = loader.loadClass(
                        "github.scarsz.discordsrv.dependencies.jda.api.requests.RestAction");
                Class<?> embedBuilderClass = loader.loadClass(
                        "github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder");
                messageEmbed = loader.loadClass(
                        "github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed");
                attachmentOption = loader.loadClass(
                        "github.scarsz.discordsrv.dependencies.jda.api.utils.AttachmentOption");

                if (configuredChannelId == null || configuredChannelId.isBlank()) {
                    channel = discordSrv.getClass().getMethod("getMainTextChannel").invoke(discordSrv);
                } else {
                    Object jda = discordSrv.getClass().getMethod("getJda").invoke(discordSrv);
                    channel = jdaClass.getMethod("getTextChannelById", String.class)
                            .invoke(jda, configuredChannelId);
                }
                if (channel == null) {
                    error = "DiscordSRV no pudo encontrar el " + selected;
                } else if (!(boolean) textChannelClass.getMethod("canTalk").invoke(channel)) {
                    error = "el bot de DiscordSRV no puede escribir en el " + selected;
                } else {
                    builderConstructor = embedBuilderClass.getConstructor();
                    color = embedBuilderClass.getMethod("setColor", int.class);
                    author = embedBuilderClass.getMethod("setAuthor", String.class);
                    description = embedBuilderClass.getMethod("setDescription", CharSequence.class);
                    field = embedBuilderClass.getMethod("addField", String.class, String.class, boolean.class);
                    image = embedBuilderClass.getMethod("setImage", String.class);
                    footer = embedBuilderClass.getMethod("setFooter", String.class);
                    timestamp = embedBuilderClass.getMethod("setTimestamp", TemporalAccessor.class);
                    build = embedBuilderClass.getMethod("build");
                    Class<?> attachmentOptions = Array.newInstance(attachmentOption, 0).getClass();
                    file = textChannelClass.getMethod("sendFile", byte[].class, String.class, attachmentOptions);
                    Class<?> messageEmbeds = Array.newInstance(messageEmbed, 0).getClass();
                    embeds = messageActionClass.getMethod("setEmbeds", messageEmbeds);
                    mentions = messageActionClass.getMethod("allowedMentions", Collection.class);
                    blockingComplete = restActionClass.getMethod("complete");
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                error = "la API de mensajes de DiscordSRV no es compatible";
                logger.warning("No se pudo preparar la entrega mediante DiscordSRV: "
                        + exception.getClass().getSimpleName());
            }
        }

        this.detected = found;
        this.accountLinkManager = links;
        this.getDiscordId = discordLookup;
        this.targetChannel = channel;
        this.embedBuilderConstructor = builderConstructor;
        this.setColor = color;
        this.setAuthor = author;
        this.setDescription = description;
        this.addField = field;
        this.setImage = image;
        this.setFooter = footer;
        this.setTimestamp = timestamp;
        this.buildEmbed = build;
        this.sendFile = file;
        this.setEmbeds = embeds;
        this.allowedMentions = mentions;
        this.complete = blockingComplete;
        this.attachmentOptionClass = attachmentOption;
        this.messageEmbedClass = messageEmbed;
        this.destinationDescription = "bot de DiscordSRV (" + selected + ")";
        this.deliveryError = error;
    }

    boolean detected() {
        return detected;
    }

    boolean available() {
        return accountLinkManager != null && getDiscordId != null;
    }

    boolean deliveryAvailable() {
        return detected && deliveryError == null && targetChannel != null && sendFile != null;
    }

    String deliveryError() {
        return deliveryError == null ? "DiscordSRV no está disponible" : deliveryError;
    }

    String discordId(UUID playerId) {
        if (!available()) {
            return null;
        }
        try {
            Object value = getDiscordId.invoke(accountLinkManager, playerId);
            return value instanceof String id && !id.isBlank() ? id : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Override
    public Result send(Settings settings, TicketService.PhotoTicket ticket,
                       ImageSanitizer.SanitizedImage photo, String caption,
                       boolean includeCoordinates) {
        if (!deliveryAvailable()) {
            return new Result(false, deliveryError());
        }
        String filename = "minemories-" + safeFilename(ticket.playerName()) + "-"
                + Instant.now().getEpochSecond() + "." + photo.extension();
        try {
            Object builder = embedBuilderConstructor.newInstance();
            setColor.invoke(builder, settings.embedColor());
            setAuthor.invoke(builder, ticket.playerName());
            setDescription.invoke(builder, caption == null || caption.isBlank()
                    ? "📸 Un nuevo recuerdo" : caption);
            addField.invoke(builder, "Servidor", settings.serverName(), true);
            if (includeCoordinates && settings.allowCoordinates()) {
                TicketService.LocationSnapshot location = ticket.location();
                addField.invoke(builder, "Ubicación",
                        location.world() + " · " + location.x() + ", " + location.y() + ", " + location.z(), true);
            }
            if (ticket.discordId() != null && !ticket.discordId().isBlank()) {
                addField.invoke(builder, "Discord vinculado", "Sí", true);
            }
            String footerText = settings.showPlayerUuid()
                    ? "Minemories • " + ticket.playerId() : "Minemories";
            setFooter.invoke(builder, footerText);
            setTimestamp.invoke(builder, Instant.now());
            setImage.invoke(builder, "attachment://" + filename);
            Object embed = buildEmbed.invoke(builder);

            Object emptyOptions = Array.newInstance(attachmentOptionClass, 0);
            Object action = sendFile.invoke(targetChannel, photo.bytes(), filename, emptyOptions);
            Object embedArray = Array.newInstance(messageEmbedClass, 1);
            Array.set(embedArray, 0, embed);
            setEmbeds.invoke(action, embedArray);
            allowedMentions.invoke(action, List.of());
            complete.invoke(action);
            return new Result(true, "");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : exception;
            logger.warning("DiscordSRV no pudo publicar una fotografía: "
                    + cause.getClass().getSimpleName());
            return new Result(false, "DiscordSRV no pudo publicar la fotografía");
        }
    }

    @Override
    public String description() {
        return destinationDescription;
    }

    private static String safeFilename(String name) {
        String safe = name == null ? "" : name.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isBlank() ? "player" : safe;
    }
}
