package dev.becerritoo.minemories.paper;

import dev.becerritoo.minemories.common.Protocol;
import dev.becerritoo.minemories.common.MapPaletteCodec;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import io.papermc.paper.event.player.CartographyItemEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

final class CameraService implements Listener, AutoCloseable {
    private static final String KIND_CAMERA = "camera";
    private static final String KIND_ROLL = "roll";
    private static final String KIND_BROKEN_CAMERA = "broken_camera";
    private static final String KIND_BROKEN_ROLL = "broken_roll";
    private final MinemoriesPlugin plugin;
    private final CameraStore store;
    private final ItemSecurity security;
    private final NamespacedKey cameraRecipe;
    private final NamespacedKey rollRecipe;
    private final NamespacedKey kindKey;
    private final NamespacedKey idKey;
    private final NamespacedKey revisionKey;
    private final NamespacedKey stateKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey maximumKey;
    private final NamespacedKey exposuresKey;
    private final NamespacedKey capacityKey;
    private final NamespacedKey keyIdKey;
    private final NamespacedKey signatureKey;
    private final NamespacedKey repairTargetKey;
    private final NamespacedKey enlargementPreviewKey;
    private final NamespacedKey copyPreviewKey;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, CaptureSession> captures = new ConcurrentHashMap<>();
    private final Map<UUID, String> captureByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, AimSession> aiming = new ConcurrentHashMap<>();

    CameraService(MinemoriesPlugin plugin) {
        this.plugin = plugin;
        this.store = new CameraStore(plugin.getDataFolder());
        this.security = ItemSecurity.load(plugin.getDataFolder(), plugin.getLogger());
        this.cameraRecipe = key("camera_recipe");
        this.rollRecipe = key("roll_recipe");
        this.kindKey = key("item_kind");
        this.idKey = key("item_id");
        this.revisionKey = key("revision");
        this.stateKey = key("state");
        this.damageKey = key("damage");
        this.maximumKey = key("maximum");
        this.exposuresKey = key("exposures");
        this.capacityKey = key("capacity");
        this.keyIdKey = key("signature_key");
        this.signatureKey = key("signature");
        this.repairTargetKey = key("repair_target");
        this.enlargementPreviewKey = key("enlargement_preview");
        this.copyPreviewKey = key("copy_preview");
        registerRecipes();
        restorePrints();
        Bukkit.getOnlinePlayers().forEach(player -> {
            discoverRecipes(player);
            migrateInventory(player);
        });
    }

    private NamespacedKey key(String value) {
        return new NamespacedKey(plugin, value);
    }

    private void registerRecipes() {
        Bukkit.removeRecipe(cameraRecipe);
        Bukkit.removeRecipe(rollRecipe);
        Bukkit.removeRecipe(key("instax_enlargement_recipe"));

        ShapedRecipe camera = new ShapedRecipe(cameraRecipe, cameraTemplate());
        camera.shape("GTG", "CSC", "RDL");
        camera.setIngredient('G', Material.GOLD_INGOT);
        camera.setIngredient('T', Material.TINTED_GLASS);
        camera.setIngredient('C', Material.COPPER_INGOT);
        camera.setIngredient('S', Material.SPYGLASS);
        camera.setIngredient('R', Material.REDSTONE);
        camera.setIngredient('D', Material.DIAMOND);
        camera.setIngredient('L', Material.GLOWSTONE_DUST);
        camera.setGroup("minemories_camera");
        camera.setCategory(CraftingBookCategory.EQUIPMENT);
        Bukkit.addRecipe(camera);

        ShapedRecipe roll = new ShapedRecipe(rollRecipe, rollTemplate());
        roll.shape("MPM", "IGI", "MRM");
        roll.setIngredient('M', Material.MAP);
        roll.setIngredient('P', Material.PAPER);
        roll.setIngredient('I', Material.INK_SAC);
        roll.setIngredient('G', Material.GLOWSTONE_DUST);
        roll.setIngredient('R', Material.REDSTONE);
        roll.setGroup("minemories_roll");
        roll.setCategory(CraftingBookCategory.MISC);
        Bukkit.addRecipe(roll);
    }

    private void discoverRecipes(Player player) {
        player.discoverRecipes(List.of(cameraRecipe, rollRecipe));
    }

    private ItemStack cameraTemplate() {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Cámara Minemories");
        meta.setLore(List.of(
                "§7Cámara fotográfica sin registrar",
                "§8Se registrará al fabricarla",
                "§8Buscar también: camara"
        ));
        meta.setItemModel(new NamespacedKey("minemories", "camera"));
        meta.setMaxStackSize(1);
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(plugin.settings().cameraDurability());
            damageable.setDamage(0);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rollTemplate() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eRollo fotográfico");
        meta.setLore(List.of("§7Rollo sin registrar", "§8Se registrará al fabricarlo"));
        meta.setItemModel(new NamespacedKey("minemories", "film_roll"));
        meta.setMaxStackSize(1);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack createCameraItem() {
        return cameraItem(store.createCamera(plugin.settings().cameraDurability()), null);
    }

    ItemStack createRollItem() {
        return rollItem(store.createRoll(plugin.settings().rollCapacity()), null);
    }

    private ItemStack cameraItem(CameraStore.CameraRecord record, ItemStack existing) {
        ItemStack item = existing == null ? new ItemStack(Material.SPYGLASS) : existing.clone();
        item.setType(Material.SPYGLASS);
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(CameraStore.WORN.equals(record.state())
                ? "§cCámara Minemories rota" : "§6Cámara Minemories");
        int remaining = Math.max(0, record.maxDamage() - record.damage());
        int exposures = store.remainingExposures(record.id());
        List<String> lore = new ArrayList<>();
        lore.add("§7Durabilidad: §f" + remaining + "§7/§f" + record.maxDamage());
        lore.add("§7Exposiciones disponibles: §f" + exposures);
        lore.add("§7Disparos realizados: §f" + record.shutterCount());
        if (CameraStore.WORN.equals(record.state())) {
            lore.add("§cRota · repárala con oro en un yunque");
        } else {
            lore.add("§8Mantén clic derecho: apuntar");
            lore.add("§8Clic izquierdo o soltar: tomar fotografía");
            lore.add("§8Agacharse + clic derecho: cargar rollo");
            lore.add("§8Agacharse + clic izquierdo: extraer rollo");
        }
        meta.setLore(lore);
        meta.setItemModel(new NamespacedKey("minemories",
                CameraStore.WORN.equals(record.state()) ? "camera_broken" : "camera"));
        meta.setMaxStackSize(1);
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(record.maxDamage());
            damageable.setDamage(record.damage());
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        clearIdentity(data);
        data.set(kindKey, PersistentDataType.STRING, KIND_CAMERA);
        data.set(idKey, PersistentDataType.STRING, record.id().toString());
        data.set(revisionKey, PersistentDataType.LONG, record.revision());
        data.set(stateKey, PersistentDataType.STRING, record.state());
        data.set(damageKey, PersistentDataType.INTEGER, record.damage());
        data.set(maximumKey, PersistentDataType.INTEGER, record.maxDamage());
        String keyId = security.currentKeyId();
        data.set(keyIdKey, PersistentDataType.STRING, keyId);
        data.set(signatureKey, PersistentDataType.STRING, security.sign(cameraCanonical(record, keyId)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rollItem(CameraStore.RollRecord record, ItemStack existing) {
        ItemStack item = existing == null ? new ItemStack(Material.PAPER) : existing.clone();
        item.setType(Material.PAPER);
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(record.exposures() >= record.capacity()
                ? "§6Rollo fotográfico revelado" : "§eRollo fotográfico");
        meta.setLore(List.of(
                "§7Fotografías: §f" + record.exposures() + "§7/§f" + record.capacity(),
                record.exposures() >= record.capacity() ? "§6Rollo completo" : "§8Puede cargarse en una cámara"
        ));
        meta.setItemModel(new NamespacedKey("minemories", "film_roll"));
        meta.setMaxStackSize(1);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        clearIdentity(data);
        data.set(kindKey, PersistentDataType.STRING, KIND_ROLL);
        data.set(idKey, PersistentDataType.STRING, record.id().toString());
        data.set(revisionKey, PersistentDataType.LONG, record.revision());
        data.set(stateKey, PersistentDataType.STRING, record.state());
        data.set(exposuresKey, PersistentDataType.INTEGER, record.exposures());
        data.set(capacityKey, PersistentDataType.INTEGER, record.capacity());
        String keyId = security.currentKeyId();
        data.set(keyIdKey, PersistentDataType.STRING, keyId);
        data.set(signatureKey, PersistentDataType.STRING, security.sign(rollCanonical(record, keyId)));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getRecipe() instanceof ShapedRecipe recipe)) {
            return;
        }
        boolean camera = recipe.getKey().equals(cameraRecipe);
        boolean roll = recipe.getKey().equals(rollRecipe);
        if (!camera && !roll) {
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            player.sendMessage("§eFabrica cámaras y rollos uno por uno para registrar su identidad.");
            return;
        }
        event.setCurrentItem(camera ? createCameraItem() : createRollItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCartography(PrepareResultEvent event) {
        if (!(event.getInventory() instanceof CartographyInventory cartography)) {
            return;
        }
        ItemStack photograph = cartography.getItem(0);
        ItemStack material = cartography.getItem(1);
        Optional<CameraStore.PrintRecord> print = print(photograph);
        if (print.isEmpty() || material == null) {
            return;
        }
        if (material.getType() == Material.MAP && print.get().gridSize() == 1) {
            ItemStack preview = photograph.clone();
            preview.setAmount(1);
            ItemMeta meta = preview.getItemMeta();
            meta.setLore(List.of("§7Copia Instax independiente",
                    "§8También podrá ampliarse"));
            meta.getPersistentDataContainer().set(copyPreviewKey, PersistentDataType.BYTE, (byte) 1);
            preview.setItemMeta(meta);
            event.setResult(preview);
            return;
        }
        if (material.getType() != Material.PAPER) {
            return;
        }
        EnlargementSource source = enlargementSource(photograph, material);
        if (source == null) {
            event.setResult(null);
            return;
        }
        ItemStack preview = photograph.clone();
        preview.setAmount(1);
        ItemMeta meta = preview.getItemMeta();
        meta.setDisplayName("§fAmpliación fotográfica " + source.gridSize() + "×" + source.gridSize());
        meta.setLore(List.of("§7Entrega §f" + source.mapCount() + " mapas",
                "§7Costo: §f" + source.paperCost() + " papeles",
                "§8Fotografía continua sin marco Instax"));
        meta.getPersistentDataContainer().set(enlargementPreviewKey,
                PersistentDataType.INTEGER, source.gridSize());
        preview.setItemMeta(meta);
        event.setResult(preview);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCartographyTake(CartographyItemEvent event) {
        if (event.getRawSlot() != 2 || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || !result.hasItemMeta()) {
            return;
        }
        PersistentDataContainer data = result.getItemMeta().getPersistentDataContainer();
        if (data.has(copyPreviewKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            craftCopy(event.getInventory(), player);
            return;
        }
        if (!data.has(enlargementPreviewKey, PersistentDataType.INTEGER)) {
            return;
        }
        event.setCancelled(true);
        craftEnlargement(event.getInventory(), player);
    }

    private void craftCopy(CartographyInventory cartography, Player player) {
        ItemStack photograph = cartography.getItem(0);
        ItemStack emptyMap = cartography.getItem(1);
        Optional<CameraStore.PrintRecord> original = print(photograph);
        if (original.isEmpty() || original.get().gridSize() != 1 || emptyMap == null
                || emptyMap.getType() != Material.MAP || emptyMap.getAmount() < 1) {
            cartography.setResult(null);
            player.sendMessage("§eSe requiere una Instax original y un mapa vacío.");
            return;
        }
        ItemStack copy = createPrintCopy(player.getWorld(), photograph, original.get());
        cartography.setItem(1, decreased(emptyMap, 1));
        cartography.setResult(null);
        Map<Integer, ItemStack> remaining = player.getInventory().addItem(copy);
        remaining.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.playSound(player.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.9f, 1.0f);
        player.sendMessage("§aInstax duplicada: ambas copias podrán ampliarse.");
    }

    private void craftEnlargement(CartographyInventory cartography, Player player) {
        ItemStack photograph = cartography.getItem(0);
        ItemStack paper = cartography.getItem(1);
        EnlargementSource source = enlargementSource(photograph, paper);
        if (source == null) {
            cartography.setResult(null);
            player.sendMessage("§eUsa exactamente 4, 9 o 16 papeles con una Instax ampliable.");
            return;
        }
        if (emptyStorageSlots(player) < source.mapCount()) {
            player.sendMessage("§eNecesitas " + source.mapCount()
                    + " espacios libres en tu inventario para recibir la ampliación.");
            return;
        }
        byte[] mural = store.render(source.print().photoId(), source.gridSize()).orElse(null);
        if (!MapPaletteCodec.validRender(mural, source.gridSize())) {
            player.sendMessage("§eEsta fotografía no contiene una versión de alta resolución.");
            return;
        }

        String title = photographTitle(photograph);
        List<ItemStack> maps = new ArrayList<>(source.mapCount());
        for (int tileY = 0; tileY < source.gridSize(); tileY++) {
            for (int tileX = 0; tileX < source.gridSize(); tileX++) {
                maps.add(createPrintTile(player.getWorld(), source.print().photoId(),
                        muralTile(mural, source.gridSize(), tileX, tileY), source.gridSize(),
                        tileX, tileY, title));
            }
        }
        CameraStore.OriginalConsumption consumption = store.consumeOriginal(source.print().mapId());
        if (!consumption.consumed()) {
            player.sendMessage("§eEsta copia de la Instax ya no es válida.");
            return;
        }
        cartography.setItem(0, decreased(photograph, 1));
        cartography.setItem(1, decreased(paper, source.paperCost()));
        cartography.setResult(null);
        for (ItemStack map : maps) {
            player.getInventory().addItem(map);
        }
        player.playSound(player.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 0.9f, 1.1f);
        player.sendMessage("§aFotografía ampliada: recibiste " + source.mapCount()
                + " mapas para un mural " + source.gridSize() + "×" + source.gridSize() + ".");
    }

    private EnlargementSource enlargementSource(ItemStack photograph, ItemStack paper) {
        if (paper == null || paper.getType() != Material.PAPER) {
            return null;
        }
        int gridSize = switch (paper.getAmount()) {
            case 4 -> 2;
            case 9 -> 3;
            case 16 -> 4;
            default -> 0;
        };
        if (gridSize == 0) {
            return null;
        }
        Optional<CameraStore.PrintRecord> print = print(photograph);
        if (print.isEmpty() || print.get().gridSize() != 1
                || store.render(print.get().photoId(), gridSize).isEmpty()) {
            return null;
        }
        return new EnlargementSource(print.get(), gridSize, paper.getAmount());
    }

    private Optional<CameraStore.PrintRecord> print(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP) {
            return Optional.empty();
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (!(itemMeta instanceof MapMeta mapMeta) || mapMeta.getMapView() == null) {
            return Optional.empty();
        }
        return store.print(mapMeta.getMapView().getId());
    }

    private static int emptyStorageSlots(Player player) {
        int empty = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                empty++;
            }
        }
        return empty;
    }

    private static ItemStack decreased(ItemStack item, int amount) {
        if (item == null || item.getAmount() <= amount) {
            return null;
        }
        ItemStack remaining = item.clone();
        remaining.setAmount(item.getAmount() - amount);
        return remaining;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        String kind = kind(item);
        if (KIND_BROKEN_CAMERA.equals(kind)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cEsta copia de la cámara está bloqueada y no puede repararse.");
            return;
        }
        if (!KIND_CAMERA.equals(kind)) {
            return;
        }
        Player player = event.getPlayer();
        CameraValidation validation = validateCamera(item);
        if (!validation.valid()) {
            event.setCancelled(true);
            invalidateCamera(player, item, validation.duplicate(), validation.message());
            return;
        }
        if (item.getType() != Material.SPYGLASS) {
            event.setCancelled(true);
            player.getInventory().setItemInMainHand(cameraItem(validation.record(), item));
            player.sendMessage("§aCámara actualizada. Ya puedes mantener clic derecho para apuntar.");
            return;
        }
        if (player.isSneaking() && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            cancelInteraction(event);
            detachRoll(player, item, validation.record());
            return;
        }
        if (player.isSneaking()) {
            cancelInteraction(event);
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (KIND_ROLL.equals(kind(offhand))) {
                attachRoll(player, item, validation.record(), offhand);
            } else {
                int exposures = store.remainingExposures(validation.record().id());
                player.sendMessage("§eColoca un rollo en la mano secundaria para cargarlo. "
                        + "§7Exposiciones disponibles: §f" + exposures + "§7.");
            }
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (!cameraReady(player, validation.record())) {
                cancelInteraction(event);
                return;
            }
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
            aiming.put(player.getUniqueId(), new AimSession(
                    validation.record().id(), validation.record().revision(), System.currentTimeMillis()));
        } else {
            // Las cámaras no se usan como herramientas o armas fuera del modo de apuntado.
            cancelInteraction(event);
        }
    }

    private static void cancelInteraction(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
    }

    private boolean cameraReady(Player player, CameraStore.CameraRecord camera) {
        if (CameraStore.WORN.equals(camera.state()) || camera.damage() >= camera.maxDamage()) {
            player.sendMessage("§cLa cámara está rota. Repárala con oro en un yunque.");
            return false;
        }
        if (store.remainingExposures(camera.id()) < 1) {
            player.sendMessage("§eCarga un rollo con exposiciones disponibles.");
            return false;
        }
        return true;
    }

    private void attachRoll(Player player, ItemStack cameraItem, CameraStore.CameraRecord camera,
                            ItemStack rollItem) {
        RollValidation roll = validateRoll(rollItem);
        if (!roll.valid()) {
            invalidateRoll(player, rollItem, roll.duplicate(), roll.message());
            return;
        }
        if (roll.record().exposures() >= roll.record().capacity()) {
            if (store.destroyRoll(roll.record().id(), roll.record().revision())) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.9f, 0.9f);
                player.sendMessage("§eEl rollo agotado se consumió.");
            } else {
                player.sendMessage("§cNo se pudo consumir el rollo agotado.");
            }
            return;
        }
        CameraStore.Attachment attachment = store.attach(camera.id(), camera.revision(),
                roll.record().id(), roll.record().revision(), plugin.settings().cameraRollSlots());
        if (!attachment.success()) {
            player.sendMessage("§c" + attachment.message());
            return;
        }
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.getInventory().setItemInMainHand(cameraItem(attachment.camera(), cameraItem));
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.8f, 1.4f);
        player.sendMessage("§aRollo cargado: " + attachment.roll().exposures() + "/"
                + attachment.roll().capacity() + " exposiciones usadas.");
    }

    private void detachRoll(Player player, ItemStack cameraItem, CameraStore.CameraRecord camera) {
        CameraStore.Attachment attachment = store.detach(camera.id(), camera.revision());
        if (!attachment.success()) {
            player.sendMessage("§e" + attachment.message());
            return;
        }
        player.getInventory().setItemInMainHand(cameraItem(attachment.camera(), cameraItem));
        ItemStack roll = rollItem(attachment.roll(), null);
        Map<Integer, ItemStack> remaining = player.getInventory().addItem(roll);
        remaining.values().forEach(value -> player.getWorld().dropItemNaturally(player.getLocation(), value));
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.8f, 1.4f);
        player.sendMessage("§aRollo extraído de la cámara.");
    }

    void fireShutter(Player player) {
        AimSession aim = aiming.remove(player.getUniqueId());
        if (aim == null || player.isSneaking() || !player.hasActiveItem()
                || player.getActiveItemHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CameraValidation validation = validateCamera(held);
        if (!matches(aim, validation)) {
            return;
        }
        // Evita que soltar el mismo uso genere una segunda fotografía.
        player.clearActiveItem();
        triggerCamera(player, validation.record());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStopUsing(PlayerStopUsingItemEvent event) {
        Player player = event.getPlayer();
        AimSession aim = aiming.remove(player.getUniqueId());
        if (aim == null || player.isSneaking()) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CameraValidation validation = validateCamera(held);
        if (matches(aim, validation)) {
            triggerCamera(player, validation.record());
        }
    }

    private static boolean matches(AimSession aim, CameraValidation validation) {
        return System.currentTimeMillis() - aim.startedAt() <= 60_000L
                && validation.valid()
                && validation.record().id().equals(aim.cameraId())
                && validation.record().revision() == aim.cameraRevision();
    }

    private void triggerCamera(Player player, CameraStore.CameraRecord camera) {
        if (!cameraReady(player, camera)) {
            return;
        }
        int remainingExposures = store.remainingExposures(camera.id());
        boolean clientAvailable = player.getListeningPluginChannels().contains(Protocol.CHANNEL);
        if (plugin.settings().forceServerRenderer()
                || (!clientAvailable && !plugin.settings().useClientRenderer())) {
            if (!plugin.settings().serverRendererEnabled()) {
                player.sendMessage("§eEl renderizador fotográfico del servidor está desactivado.");
                return;
            }
            plugin.requestServerCapture(player, camera);
            return;
        }
        if (!clientAvailable) {
            player.sendMessage("§eEste servidor exige Minemories Fabric para capturar la imagen.");
            return;
        }
        purgeExpiredCaptures();
        String token = token();
        CaptureSession session = new CaptureSession(player.getUniqueId(), camera.id(), camera.revision(),
                System.currentTimeMillis() + plugin.settings().cameraCaptureSessionSeconds() * 1_000L);
        String previous = captureByPlayer.put(player.getUniqueId(), token);
        if (previous != null) {
            captures.remove(previous);
        }
        captures.put(token, session);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_DISPENSER_LAUNCH, 0.9f, 1.7f);
        player.getWorld().spawnParticle(Particle.FLASH, player.getEyeLocation().add(
                player.getLocation().getDirection().multiply(0.45)), 1, org.bukkit.Color.WHITE);
        int remainingDurability = Math.max(0, camera.maxDamage() - camera.damage());
        player.sendPluginMessage(plugin, Protocol.CHANNEL, Protocol.encodeCameraTrigger(
                new Protocol.CameraTrigger(token, remainingDurability, remainingExposures)));
    }

    Optional<CaptureReservation> reserve(Player player, String token) {
        purgeExpiredCaptures();
        CaptureSession session = captures.remove(token);
        if (session == null || !session.playerId().equals(player.getUniqueId())
                || session.expiresAt() < System.currentTimeMillis()) {
            return Optional.empty();
        }
        captureByPlayer.remove(player.getUniqueId(), token);
        ItemStack held = player.getInventory().getItemInMainHand();
        CameraValidation validation = validateCamera(held);
        if (!validation.valid() || !validation.record().id().equals(session.cameraId())
                || validation.record().revision() != session.cameraRevision()) {
            if (!validation.valid()) {
                invalidateCamera(player, held, validation.duplicate(), validation.message());
            }
            return Optional.empty();
        }
        return reserveValidated(player, held, session.cameraId(), session.cameraRevision());
    }

    Optional<CaptureReservation> reserveDirect(Player player, UUID cameraId, long cameraRevision) {
        ItemStack held = player.getInventory().getItemInMainHand();
        CameraValidation validation = validateCamera(held);
        if (!validation.valid() || !validation.record().id().equals(cameraId)
                || validation.record().revision() != cameraRevision) {
            if (!validation.valid()) {
                invalidateCamera(player, held, validation.duplicate(), validation.message());
            }
            return Optional.empty();
        }
        return reserveValidated(player, held, cameraId, cameraRevision);
    }

    private Optional<CaptureReservation> reserveValidated(Player player, ItemStack held,
                                                           UUID cameraId, long cameraRevision) {
        int unbreaking = held.getEnchantmentLevel(Enchantment.UNBREAKING);
        boolean consumeDurability = unbreaking < 1 || ThreadLocalRandom.current().nextInt(unbreaking + 1) == 0;
        CameraStore.ShotResult shot = store.reserveShot(cameraId, cameraRevision, consumeDurability);
        if (!shot.success()) {
            if (shot.status() == CameraStore.ShotStatus.DUPLICATE) {
                invalidateCamera(player, held, true, shot.message());
            } else {
                player.sendMessage("§c" + shot.message());
            }
            return Optional.empty();
        }
        player.getInventory().setItemInMainHand(cameraItem(shot.camera(), held));
        if (shot.rollConsumed()) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.9f, 1.15f);
            player.sendMessage("§6El rollo usó su última exposición y se consumió.");
        }
        return Optional.of(new CaptureReservation(shot.photoId(), shot.camera().id(), shot.roll().id()));
    }

    ItemStack createPrint(org.bukkit.World world, UUID photoId, byte[] pixels, String playerName,
                          String caption) {
        MapView map = Bukkit.createMap(world);
        installRenderer(map, pixels);
        store.savePrint(photoId, map.getId(), pixels);
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(map);
        meta.setDisplayName("§f" + itemTitle(caption));
        meta.setLore(List.of("§7Tomada por §f" + playerName,
                "§8Puede duplicarse o ampliarse en cartografía"));
        item.setItemMeta(meta);
        return item;
    }

    void saveMural(UUID photoId, byte[] pixels) {
        if (!MapPaletteCodec.validMural(pixels)) {
            throw new IllegalArgumentException("La ampliación Instax no es válida");
        }
        store.saveRender(photoId, 4, pixels);
        store.saveRender(photoId, 3, MapPaletteCodec.resize(pixels,
                MapPaletteCodec.MURAL_SIZE, MapPaletteCodec.MAP_SIZE * 3));
        store.saveRender(photoId, 2, MapPaletteCodec.resize(pixels,
                MapPaletteCodec.MURAL_SIZE, MapPaletteCodec.MAP_SIZE * 2));
    }

    private ItemStack createPrintCopy(org.bukkit.World world, ItemStack original,
                                      CameraStore.PrintRecord print) {
        MapView map = Bukkit.createMap(world);
        installRenderer(map, print.pixels());
        store.savePrint(print.photoId(), map.getId(), print.pixels());
        ItemStack item = original.clone();
        item.setAmount(1);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(map);
        meta.getPersistentDataContainer().remove(copyPreviewKey);
        meta.getPersistentDataContainer().remove(enlargementPreviewKey);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPrintTile(org.bukkit.World world, UUID photoId, byte[] pixels,
                                      int gridSize, int tileX, int tileY, String title) {
        MapView map = Bukkit.createMap(world);
        installRenderer(map, pixels);
        store.savePrint(photoId, map.getId(), pixels, gridSize, tileX, tileY);
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(map);
        int index = tileY * gridSize + tileX + 1;
        int total = gridSize * gridSize;
        meta.setDisplayName("§f" + title + " §7(" + index + "/" + total + ")");
        meta.setLore(List.of("§7Fila §f" + (tileY + 1) + "§7, columna §f" + (tileX + 1),
                "§8Puede duplicarse en una mesa de cartografía"));
        item.setItemMeta(meta);
        return item;
    }

    private static byte[] muralTile(byte[] mural, int gridSize, int tileX, int tileY) {
        byte[] tile = new byte[MapPaletteCodec.PIXEL_COUNT];
        int muralSize = MapPaletteCodec.MAP_SIZE * gridSize;
        for (int y = 0; y < MapPaletteCodec.MAP_SIZE; y++) {
            int sourceOffset = (tileY * MapPaletteCodec.MAP_SIZE + y) * muralSize
                    + tileX * MapPaletteCodec.MAP_SIZE;
            System.arraycopy(mural, sourceOffset, tile, y * MapPaletteCodec.MAP_SIZE,
                    MapPaletteCodec.MAP_SIZE);
        }
        return tile;
    }

    private static String photographTitle(ItemStack photograph) {
        if (photograph != null && photograph.hasItemMeta()) {
            ItemMeta meta = photograph.getItemMeta();
            if (meta.hasDisplayName()) {
                String title = cleanTitle(meta.getDisplayName());
                if (!title.isBlank()) {
                    return title;
                }
            }
        }
        return "Fotografía Minemories";
    }

    static String itemTitle(String caption) {
        String title = cleanTitle(caption);
        return title.isBlank() ? "Fotografía Minemories" : title;
    }

    private static String cleanTitle(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder clean = new StringBuilder();
        boolean formatting = false;
        for (int offset = 0; offset < input.length();) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (formatting) {
                formatting = false;
                continue;
            }
            if (codePoint == '§') {
                formatting = true;
                continue;
            }
            if (Character.isWhitespace(codePoint)) {
                if (!clean.isEmpty() && clean.charAt(clean.length() - 1) != ' ') {
                    clean.append(' ');
                }
            } else if (!Character.isISOControl(codePoint)) {
                clean.appendCodePoint(codePoint);
            }
        }
        String value = clean.toString().strip();
        int count = value.codePointCount(0, value.length());
        if (count <= 64) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, 64)).stripTrailing();
    }

    private void restorePrints() {
        int restored = 0;
        for (CameraStore.PrintRecord print : store.prints()) {
            MapView map = Bukkit.getMap(print.mapId());
            if (map != null) {
                installRenderer(map, print.pixels());
                restored++;
            }
        }
        if (restored > 0) {
            plugin.getLogger().info("Impresiones fotográficas restauradas: " + restored + ".");
        }
    }

    private static void installRenderer(MapView map, byte[] pixels) {
        for (var renderer : List.copyOf(map.getRenderers())) {
            map.removeRenderer(renderer);
        }
        map.setTrackingPosition(false);
        map.setUnlimitedTracking(false);
        map.setLocked(true);
        map.addRenderer(new StaticPhotoMapRenderer(pixels));
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (first == null || !KIND_CAMERA.equals(kind(first))) {
            return;
        }
        if (second != null && KIND_CAMERA.equals(kind(second))) {
            event.setResult(null);
            return;
        }
        if (second == null || second.getType() != Material.GOLD_INGOT) {
            return;
        }
        CameraValidation validation = validateCamera(first);
        if (!validation.valid() || validation.record().damage() <= 0) {
            event.setResult(null);
            return;
        }
        int perGold = plugin.settings().repairPerGold();
        int materials = Math.min(second.getAmount(),
                (validation.record().damage() + perGold - 1) / perGold);
        int target = Math.max(0, validation.record().damage() - materials * perGold);
        ItemStack preview = cameraItem(validation.record(), first);
        ItemMeta meta = preview.getItemMeta();
        meta.getPersistentDataContainer().set(repairTargetKey, PersistentDataType.INTEGER, target);
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(target);
        }
        preview.setItemMeta(meta);
        event.getView().setRepairItemCountCost(materials);
        event.getView().setRepairCost(1 + materials);
        event.getView().setMaximumRepairCost(1000);
        event.setResult(preview);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilTake(InventoryClickEvent event) {
        if (event.getRawSlot() != 2 || !(event.getView().getTopInventory() instanceof org.bukkit.inventory.AnvilInventory anvil)) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        ItemStack first = anvil.getFirstItem();
        if (result == null || first == null) {
            return;
        }
        Integer target = result.getItemMeta().getPersistentDataContainer()
                .get(repairTargetKey, PersistentDataType.INTEGER);
        if (target == null) {
            return;
        }
        CameraValidation validation = validateCamera(first);
        if (!validation.valid()) {
            event.setCancelled(true);
            return;
        }
        Optional<CameraStore.CameraRecord> repaired = store.repair(
                validation.record().id(), validation.record().revision(), target);
        if (repaired.isEmpty()) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage("§cLa cámara cambió antes de completar la reparación.");
            }
            return;
        }
        event.setCurrentItem(cameraItem(repaired.get(), first));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMend(PlayerItemMendEvent event) {
        ItemStack item = event.getItem();
        if (!KIND_CAMERA.equals(kind(item))) {
            return;
        }
        CameraValidation validation = validateCamera(item);
        if (!validation.valid()) {
            event.setCancelled(true);
            invalidateCamera(event.getPlayer(), item, validation.duplicate(), validation.message());
            return;
        }
        int target = Math.max(0, validation.record().damage() - event.getRepairAmount());
        Optional<CameraStore.CameraRecord> repaired = store.repair(
                validation.record().id(), validation.record().revision(), target);
        if (repaired.isEmpty()) {
            event.setCancelled(true);
            return;
        }
        ItemStack updated = cameraItem(repaired.get(), item);
        ItemMeta meta = updated.getItemMeta();
        if (meta instanceof Damageable damageable) {
            // Vanilla aplicará el valor de reparación al salir del evento.
            damageable.setDamage(validation.record().damage());
        }
        updated.setItemMeta(meta);
        item.setItemMeta(updated.getItemMeta());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        discoverRecipes(event.getPlayer());
        migrateInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        aiming.remove(playerId);
        invalidateCapture(playerId);
    }

    private void migrateInventory(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            String itemKind = kind(item);
            if (KIND_CAMERA.equals(itemKind)) {
                CameraValidation validation = validateCamera(item);
                if (validation.valid() && item.getType() != Material.SPYGLASS) {
                    player.getInventory().setItem(slot, cameraItem(validation.record(), item));
                }
            } else if (KIND_BROKEN_CAMERA.equals(itemKind) && item.getType() != Material.SPYGLASS) {
                ItemStack migrated = item.clone();
                migrated.setType(Material.SPYGLASS);
                player.getInventory().setItem(slot, migrated);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        destroy(event.getEntity().getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Item item)) {
            return;
        }
        EntityRemoveEvent.Cause cause = event.getCause();
        if (cause == EntityRemoveEvent.Cause.DESPAWN || cause == EntityRemoveEvent.Cause.OUT_OF_WORLD
                || cause == EntityRemoveEvent.Cause.DEATH || cause == EntityRemoveEvent.Cause.EXPLODE
                || cause == EntityRemoveEvent.Cause.DISCARD) {
            destroy(item.getItemStack());
        }
    }

    private void destroy(ItemStack item) {
        if (KIND_CAMERA.equals(kind(item))) {
            CameraValidation validation = validateCamera(item);
            if (validation.valid()) {
                store.destroyCamera(validation.record().id(), validation.record().revision());
            }
        } else if (KIND_ROLL.equals(kind(item))) {
            RollValidation validation = validateRoll(item);
            if (validation.valid()) {
                store.destroyRoll(validation.record().id(), validation.record().revision());
            }
        } else {
            Optional<CameraStore.PrintRecord> print = print(item);
            if (print.isPresent() && print.get().gridSize() == 1) {
                store.consumeOriginal(print.get().mapId());
            }
        }
    }

    private CameraValidation validateCamera(ItemStack item) {
        try {
            ItemMeta meta = item == null ? null : item.getItemMeta();
            if (meta == null || !KIND_CAMERA.equals(kind(item))) {
                return CameraValidation.failure(false, "No es una cámara registrada.");
            }
            PersistentDataContainer data = meta.getPersistentDataContainer();
            UUID id = UUID.fromString(required(data, idKey, PersistentDataType.STRING));
            long revision = required(data, revisionKey, PersistentDataType.LONG);
            String state = required(data, stateKey, PersistentDataType.STRING);
            int damage = required(data, damageKey, PersistentDataType.INTEGER);
            int maximum = required(data, maximumKey, PersistentDataType.INTEGER);
            String keyId = required(data, keyIdKey, PersistentDataType.STRING);
            String signature = required(data, signatureKey, PersistentDataType.STRING);
            CameraStore.CameraRecord itemRecord = new CameraStore.CameraRecord(
                    id, revision, state, damage, 0, maximum);
            if (!security.verify(keyId, cameraCanonical(itemRecord, keyId), signature)) {
                return CameraValidation.failure(false, "La firma de la cámara fue alterada.");
            }
            Optional<CameraStore.CameraRecord> stored = store.camera(id);
            if (stored.isEmpty()) {
                return CameraValidation.failure(false, "La cámara no existe en el registro.");
            }
            CameraStore.CameraRecord record = stored.get();
            if (revision != record.revision()) {
                return CameraValidation.failure(revision < record.revision(),
                        revision < record.revision() ? "Se detectó una copia duplicada." : "La revisión no es válida.");
            }
            int actualDamage = meta instanceof Damageable damageable ? damageable.getDamage() : -1;
            if (!state.equals(record.state()) || damage != record.damage() || maximum != record.maxDamage()
                    || actualDamage != damage) {
                return CameraValidation.failure(false, "El estado de la cámara fue alterado.");
            }
            return CameraValidation.success(record);
        } catch (RuntimeException exception) {
            return CameraValidation.failure(false, "Los datos de la cámara están dañados.");
        }
    }

    private RollValidation validateRoll(ItemStack item) {
        try {
            ItemMeta meta = item == null ? null : item.getItemMeta();
            if (meta == null || !KIND_ROLL.equals(kind(item))) {
                return RollValidation.failure(false, "No es un rollo registrado.");
            }
            PersistentDataContainer data = meta.getPersistentDataContainer();
            UUID id = UUID.fromString(required(data, idKey, PersistentDataType.STRING));
            long revision = required(data, revisionKey, PersistentDataType.LONG);
            String state = required(data, stateKey, PersistentDataType.STRING);
            int exposures = required(data, exposuresKey, PersistentDataType.INTEGER);
            int capacity = required(data, capacityKey, PersistentDataType.INTEGER);
            String keyId = required(data, keyIdKey, PersistentDataType.STRING);
            String signature = required(data, signatureKey, PersistentDataType.STRING);
            CameraStore.RollRecord itemRecord = new CameraStore.RollRecord(
                    id, revision, state, exposures, capacity, null, null);
            if (!security.verify(keyId, rollCanonical(itemRecord, keyId), signature)) {
                return RollValidation.failure(false, "La firma del rollo fue alterada.");
            }
            Optional<CameraStore.RollRecord> stored = store.roll(id);
            if (stored.isEmpty()) {
                return RollValidation.failure(false, "El rollo no existe en el registro.");
            }
            CameraStore.RollRecord record = stored.get();
            if (record.cameraId() != null) {
                return RollValidation.failure(true, "Ese rollo ya está dentro de una cámara.");
            }
            if (revision != record.revision()) {
                return RollValidation.failure(revision < record.revision(), "Se detectó una copia duplicada.");
            }
            if (!state.equals(record.state()) || exposures != record.exposures() || capacity != record.capacity()) {
                return RollValidation.failure(false, "El estado del rollo fue alterado.");
            }
            return RollValidation.success(record);
        } catch (RuntimeException exception) {
            return RollValidation.failure(false, "Los datos del rollo están dañados.");
        }
    }

    private void invalidateCamera(Player player, ItemStack item, boolean duplicate, String reason) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(duplicate ? "§4Cámara duplicada" : "§4Cámara corrupta");
        meta.setLore(List.of("§cObjeto bloqueado permanentemente", "§7" + reason,
                "§8No puede repararse ni abrir rollos"));
        meta.setItemModel(new NamespacedKey("minemories", "camera_broken"));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, KIND_BROKEN_CAMERA);
        meta.getPersistentDataContainer().remove(signatureKey);
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(Math.max(1, damageable.getMaxDamage()));
        }
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
        player.sendMessage("§c" + reason + " La copia quedó bloqueada; la cámara vigente no fue afectada.");
        plugin.getLogger().warning("Cámara inválida detectada en " + player.getName() + " ("
                + player.getUniqueId() + "): " + reason);
    }

    private void invalidateRoll(Player player, ItemStack item, boolean duplicate, String reason) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(duplicate ? "§4Rollo duplicado" : "§4Rollo velado");
        meta.setLore(List.of("§cRollo bloqueado permanentemente", "§7" + reason));
        meta.setItemModel(new NamespacedKey("minemories", "film_roll"));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, KIND_BROKEN_ROLL);
        meta.getPersistentDataContainer().remove(signatureKey);
        item.setItemMeta(meta);
        player.getInventory().setItemInOffHand(item);
        player.sendMessage("§c" + reason + " El rollo inválido quedó bloqueado.");
        plugin.getLogger().warning("Rollo inválido detectado en " + player.getName() + " ("
                + player.getUniqueId() + "): " + reason);
    }

    private String kind(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return "";
        }
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(
                kindKey, PersistentDataType.STRING, "");
    }

    private void clearIdentity(PersistentDataContainer data) {
        data.remove(kindKey);
        data.remove(idKey);
        data.remove(revisionKey);
        data.remove(stateKey);
        data.remove(damageKey);
        data.remove(maximumKey);
        data.remove(exposuresKey);
        data.remove(capacityKey);
        data.remove(keyIdKey);
        data.remove(signatureKey);
        data.remove(repairTargetKey);
    }

    private static <P, C> C required(PersistentDataContainer data, NamespacedKey key,
                                     PersistentDataType<P, C> type) {
        C value = data.get(key, type);
        if (value == null) {
            throw new IllegalArgumentException("Falta " + key);
        }
        return value;
    }

    private static String cameraCanonical(CameraStore.CameraRecord record, String keyId) {
        return "1|camera|" + record.id() + '|' + record.revision() + '|' + record.state() + '|'
                + record.damage() + '|' + record.maxDamage() + '|' + keyId;
    }

    private static String rollCanonical(CameraStore.RollRecord record, String keyId) {
        return "1|roll|" + record.id() + '|' + record.revision() + '|' + record.state() + '|'
                + record.exposures() + '|' + record.capacity() + '|' + keyId;
    }

    private String token() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void purgeExpiredCaptures() {
        long now = System.currentTimeMillis();
        captures.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expiresAt() < now;
            if (expired) {
                captureByPlayer.remove(entry.getValue().playerId(), entry.getKey());
            }
            return expired;
        });
    }

    private void invalidateCapture(UUID playerId) {
        String token = captureByPlayer.remove(playerId);
        if (token != null) {
            captures.remove(token);
        }
    }

    CameraStore.Stats stats() {
        return store.stats();
    }

    void give(Player player, boolean camera, int amount) {
        for (int index = 0; index < amount; index++) {
            ItemStack item = camera ? createCameraItem() : createRollItem();
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(item);
            remaining.values().forEach(value -> player.getWorld().dropItemNaturally(player.getLocation(), value));
        }
    }

    @Override
    public void close() {
        captures.clear();
        captureByPlayer.clear();
        aiming.clear();
        Bukkit.removeRecipe(cameraRecipe);
        Bukkit.removeRecipe(rollRecipe);
        Bukkit.removeRecipe(key("instax_enlargement_recipe"));
        store.close();
    }

    record CaptureReservation(UUID photoId, UUID cameraId, UUID rollId) {
    }

    private record CaptureSession(UUID playerId, UUID cameraId, long cameraRevision, long expiresAt) {
    }

    private record AimSession(UUID cameraId, long cameraRevision, long startedAt) {
    }

    private record CameraValidation(boolean valid, boolean duplicate, String message,
                                    CameraStore.CameraRecord record) {
        static CameraValidation success(CameraStore.CameraRecord record) {
            return new CameraValidation(true, false, "", record);
        }

        static CameraValidation failure(boolean duplicate, String message) {
            return new CameraValidation(false, duplicate, message, null);
        }
    }

    private record RollValidation(boolean valid, boolean duplicate, String message,
                                  CameraStore.RollRecord record) {
        static RollValidation success(CameraStore.RollRecord record) {
            return new RollValidation(true, false, "", record);
        }

        static RollValidation failure(boolean duplicate, String message) {
            return new RollValidation(false, duplicate, message, null);
        }
    }

    private record EnlargementSource(CameraStore.PrintRecord print, int gridSize, int paperCost) {
        int mapCount() {
            return gridSize * gridSize;
        }
    }
}
