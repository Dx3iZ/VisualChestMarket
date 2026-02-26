package org.dxeiz.visualchestmarket.core;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.*;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.dxeiz.visualchestmarket.VisualChestMarket;
import org.dxeiz.visualchestmarket.model.DisplayMode;
import org.dxeiz.visualchestmarket.model.Shop;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayManager {

    private final VisualChestMarket plugin;
    private final Map<Location, Entity[]> activeArmorStands = new ConcurrentHashMap<>();
    private final Map<Location, Hologram> activeHolograms = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> headCache = new HashMap<>();
    private final NamespacedKey displayKey;

    // ArmorStand Havuzu (Pooling)
    private final Queue<ArmorStand> armorStandPool = new LinkedList<>();
    private static final int POOL_SIZE = 100;

    // Görünürlük Mesafeleri (Kareleri alınmıştır: 20x20=400, 30x30=900)
    private static final double VIEW_DISTANCE_SQ = 400; // 20 Blokta görünür
    private static final double HIDE_DISTANCE_SQ = 900; // 30 Blokta kaybolur (Tampon bölge)

    // Kafa UUID'leri (Sabitler)
    private static final UUID UUID_WEAPON = UUID.fromString("615a0d98-c595-4b9a-97b6-d4a0d74c5030");
    private static final UUID UUID_ARMOR = UUID.fromString("5bc9fd7d-3a9c-4025-b332-a7fd8fc9b142");
    private static final UUID UUID_BLOCK = UUID.fromString("10e82a3a-41b8-4a67-9a70-78135f928f6d");
    private static final UUID UUID_TOOL = UUID.fromString("e173ba5a-7714-4afe-a18f-7e4372f5b3e7");
    private static final UUID UUID_REDSTONE = UUID.fromString("5b5dbb2f-528b-428c-86b7-887a368f1b6b");
    private static final UUID UUID_MAGIC = UUID.fromString("fd8766cd-b329-4bb9-90e2-d86a6ed6ab3b");
    private static final UUID UUID_DEFAULT = UUID.fromString("cf7cde2f-ee4f-4496-3655-85a4fc8cd8e3");

    public DisplayManager(VisualChestMarket plugin) {
        this.plugin = plugin;
        this.displayKey = new NamespacedKey(plugin, "vcm_entity");
        startCullingTask();
    }

    public void cleanupAllWorldDisplays() {
        // Tüm dünyalardaki eklentiye ait varlıkları temizle
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(displayKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
        activeArmorStands.clear();

        // Hologramları temizle
        for (Hologram holo : activeHolograms.values()) {
            if (holo != null) DHAPI.removeHologram(holo.getName());
        }
        activeHolograms.clear();

        // Havuzu temizle
        armorStandPool.forEach(Entity::remove);
        armorStandPool.clear();
    }

    public void updateDisplay(Shop shop) {
        removeDisplay(shop.getLocation());

        if (shop.getLocation().getBlock().getType() != Material.CHEST &&
                shop.getLocation().getBlock().getType() != Material.TRAPPED_CHEST) return;

        if (shop.getDisplayMode() == DisplayMode.NONE) return;
        if (!shop.getLocation().getChunk().isLoaded()) return;

        Location centerLoc = shop.getLocation().clone().add(0.5, 0, 0.5);

        if (shop.getDisplayMode() == DisplayMode.ARMORSTAND) {
            // ArmorStand başlangıçta boş (null) olarak kaydedilir, Culling Task oluşturur
            activeArmorStands.put(shop.getLocation(), new Entity[]{null});
        }
        else {
            // Hologram (ItemDisplay)
            Location itemLoc = centerLoc.clone().add(0, 1.5, 0);
            String holoName = "vcm_" + shop.getId();

            // Eğer hologram zaten varsa sil
            if (DHAPI.getHologram(holoName) != null) {
                DHAPI.removeHologram(holoName);
            }

            Hologram hologram = DHAPI.createHologram(holoName, itemLoc);
            DHAPI.addHologramLine(hologram, shop.getItem());
            activeHolograms.put(shop.getLocation(), hologram);
        }
    }

    // --- CULLING & OPTIMIZATION (Görüş Açısı Yönetimi) ---

    private void startCullingTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeArmorStands.isEmpty()) return;

            // Online oyuncuların konumlarını önbelleğe al (Performans için)
            List<Location> playerLocations = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                playerLocations.add(p.getLocation());
            }

            for (Map.Entry<Location, Entity[]> entry : activeArmorStands.entrySet()) {
                Location shopLoc = entry.getKey();
                Entity[] entities = entry.getValue();

                boolean shouldBeVisible = false;

                // En yakın oyuncuyu kontrol et
                if (shopLoc.getWorld() != null) {
                    for (Location pLoc : playerLocations) {
                        if (pLoc.getWorld() == shopLoc.getWorld()) {
                            double distSq = pLoc.distanceSquared(shopLoc);

                            // Hysteresis: Görünüyorsa 30 blok, görünmüyorsa 20 blok sınırı
                            double threshold = (entities[0] != null) ? HIDE_DISTANCE_SQ : VIEW_DISTANCE_SQ;

                            if (distSq <= threshold) {
                                shouldBeVisible = true;
                                break;
                            }
                        }
                    }
                }

                if (shouldBeVisible) {
                    if (entities[0] == null || !entities[0].isValid()) {
                        Shop shop = plugin.getShopManager().getShop(shopLoc);
                        if (shop != null) {
                            // SANDIK ÜSTÜ AYARI: y + 0.9 ArmorStand'in ayaklarını sandık kapağının tam üstüne koyar
                            Location standLoc = shopLoc.clone().add(0.5, 0.9, 0.5);
                            ArmorStand stand = getArmorStandFromPool(standLoc);
                            setupMascot(stand, shop);
                            entry.setValue(new Entity[]{stand});
                        }
                    }
                } else {
                    // Görüntülenmemesi gerekiyor ama varlık varsa havuza gönder
                    if (entities[0] != null && entities[0].isValid()) {
                        returnArmorStandToPool((ArmorStand) entities[0]);
                        entry.setValue(new Entity[]{null});
                    }
                }
            }
        }, 20L, 20L); // Her 1 saniyede bir çalışır (Sunucuyu yormaz)
    }

    // --- POOLING SYSTEM (Havuzlama) ---

    private ArmorStand getArmorStandFromPool(Location loc) {
        ArmorStand stand = armorStandPool.poll();
        if (stand == null || !stand.isValid()) {
            stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        } else {
            // Havuzdan gelen varlığı temizle ve ışınla
            stand.teleport(loc);
            stand.setFireTicks(0);
            stand.setVisualFire(false);
        }
        return stand;
    }

    private void returnArmorStandToPool(ArmorStand stand) {
        if (armorStandPool.size() < POOL_SIZE) {
            // Yerin dibine ışınla ve sakla
            stand.teleport(new Location(stand.getWorld(), 0, -500, 0));
            armorStandPool.offer(stand);
        } else {
            stand.remove();
        }
    }

    // --- MASCOT SETUP & VISUALS ---

    private void setupMascot(ArmorStand stand, Shop shop) {
        stand.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, "true");
        stand.setSmall(true);
        stand.setInvisible(false);
        stand.setBasePlate(false);
        stand.setGravity(false);
        stand.setArms(true);
        stand.setInvulnerable(true);
        stand.setMarker(true); // Tıklanamaz ve içinden geçilebilir yapar

        // Ekipman kilitleri
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stand.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
            stand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }

        // Yön Ayarı (Tabelaya veya sandığa göre)
        if (shop.getLocation().getBlock().getBlockData() instanceof Directional dir) {
            float yaw = switch (dir.getFacing()) {
                case NORTH -> 180;
                case SOUTH -> 0;
                case WEST -> 90;
                case EAST -> -90;
                default -> 0;
            };
            Location loc = stand.getLocation();
            loc.setYaw(yaw);
            stand.teleport(loc);
        }

        applyCategorySettings(stand, shop.getItem());

        // Elinde eşya tutma mantığı
        // Bazı eşyalar (bloklar) özel muamele görür, applyCategorySettings içinde halledilir.
        if (!shop.getItem().getType().isBlock()) {
            stand.getEquipment().setItemInMainHand(shop.getItem());
        }
    }

    private void applyCategorySettings(ArmorStand stand, ItemStack item) {
        Material type = item.getType();
        String name = type.name();

        // Sol kolun varsayılan duruşunu düzelt (Vücuda yapışmasın, doğal dursun)
        // Sol kol: Hafif yana ve öne açık
        float[] defaultLeftArm = new float[]{-10f, 0f, -10f};

        // 1. BLOKLAR (Mimar Duruşu: Bloğu kucaklamış gibi önde tutar)
        if (type.isBlock()) {
            setPose(stand,
                    new float[]{25f, 0f, 0f},    // Kafa: Aşağı, elindeki devreye bakar
                    new float[]{-45f, 27f, 0f},     // Sol Kol: Doğal salınım
                    new float[]{-45f, -27f, 0f},   // Sağ Kol: DÜZELTİLDİ. Geriye değil, hafif öne ve aşağı uzatır.
                    new float[]{5f, 0f, -10f}, new float[]{-5f, 0f, 10f}
            );
            stand.getEquipment().setItemInMainHand(item);
            setArmor(stand, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, UUID_BLOCK, Color.ORANGE);
        }
        // 3. SİLAHLAR (Savaşçı Duruşu: Trident kafaya girmesin diye kolu yana açar)
        else if (Tag.ITEMS_SWORDS.isTagged(type) || type == Material.TRIDENT || type == Material.BOW || type == Material.CROSSBOW) {
            setPose(stand,
                    new float[]{0f, -15f, 0f},   // Kafa: Hafif sola bakar (artistik)
                    new float[]{-10f, 0f, -20f},  // Sol Kol: Hafif yana açık
                    // Sağ Kol: DÜZELTİLDİ. -90 tam karşıdır. -50 hafif aşağıdır. 45 yana açar.
                    // Bu açı silahı vücuttan uzaklaştırır, kafaya girmesini engeller.
                    new float[]{-90f, 45f, 0f},
                    new float[]{5f, 0f, -10f}, new float[]{-5f, 0f, 10f} // Bacaklar: Hafif adım atmış
            );
            stand.getEquipment().setItemInMainHand(item);
            setArmor(stand, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, UUID_WEAPON, null);
        }

        // 3. ALETLER (İşçi/Madenci Pozisyonu: Aleti omuzunda gururla taşır)
        else if (Tag.ITEMS_PICKAXES.isTagged(type) || Tag.ITEMS_SHOVELS.isTagged(type) || Tag.ITEMS_HOES.isTagged(type) || Tag.ITEMS_AXES.isTagged(type)) {
            setPose(stand,
                    new float[]{0f, 0f, 0f},
                    new float[]{10f, 0f, -10f},
                    new float[]{-50f, -50f, 0f}, // Sağ Kol: Aleti omuza yaslamış (Eşya yukarı bakar)
                    new float[]{5f, 0f, -10f}, new float[]{-5f, 0f, 10f}
            );
            stand.getEquipment().setItemInMainHand(item);
            setArmor(stand, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, UUID_TOOL, Color.YELLOW);
        }

        // 4. BÜYÜ & İKSİR (Büyücü Duruşu: Eşyayı havaya kaldırır ama yüzden uzak tutar)
        else if (name.contains("POTION") || name.contains("BOOK") || name.contains("ENCHANT") || type == Material.NETHER_STAR) {
            setPose(stand,
                    new float[]{-20f, 0f, 0f},   // Kafa: Yukarı bakar
                    new float[]{-100f, 0f, 0f},  // Sol Kol: Havaya açık
                    new float[]{-100f, -20f, 0f}, // Sağ Kol: Havaya ve hafif dışa açık (Yüze girmemesi için)
                    new float[]{5f, 0f, -10f}, new float[]{-5f, 0f, 10f}
            );
            stand.getEquipment().setItemInMainHand(item);
            setArmor(stand, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, UUID_MAGIC, Color.PURPLE);
        }

        // 2. REDSTONE (Mühendis Duruşu: Eşyayı kafasına sokmaz, göğüs hizasında inceler)
        else if (name.contains("REDSTONE") || name.contains("WIRE") || name.contains("RAIL") ||
                name.contains("PISTON") || name.contains("REPEATER") || name.contains("COMPARATOR") ||
                type == Material.LEVER || type == Material.OBSERVER || type == Material.HOPPER) {

            setPose(stand,
                    new float[]{25f, 0f, 0f},    // Kafa: Aşağı, elindeki devreye bakar
                    defaultLeftArm,              // Sol Kol: Doğal salınım
                    new float[]{-45f, 0f, 0f},   // Sağ Kol: DÜZELTİLDİ. Geriye değil, hafif öne ve aşağı uzatır.
                    new float[]{5f, 0f, -10f}, new float[]{-5f, 0f, 10f}
            );
            stand.getEquipment().setItemInMainHand(item);
            setArmor(stand, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, UUID_REDSTONE, Color.RED);
        }

        // 5. ZIRH (Manken Duruşu: Dik ve net)
        else if (name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS") || name.contains("BOOTS") || type == Material.ELYTRA) {
            setPose(stand,
                    new float[]{0f, 0f, 0f},
                    new float[]{10f, 0f, -10f},
                    new float[]{10f, 0f, 10f},
                    new float[]{5f, 0f, -10f}, new float[]{-5f, 0f, 10f}
            );

            stand.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
            stand.getEquipment().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
            stand.getEquipment().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));

            if (name.contains("HELMET")) stand.getEquipment().setHelmet(item);
            else if (name.contains("CHESTPLATE")) stand.getEquipment().setChestplate(item);
            else if (name.contains("LEGGINGS")) stand.getEquipment().setLeggings(item);
            else if (name.contains("BOOTS")) stand.getEquipment().setBoots(item);
            else if (type == Material.ELYTRA) stand.getEquipment().setChestplate(item);

            stand.getEquipment().setHelmet(getHeadFromUUID(UUID_ARMOR));
        }
        // 6. YİYECEK & TARIM (Yeme Duruşu)
        else if (type.isEdible() || Tag.ITEMS_HOES.isTagged(type) || name.contains("SEED") || name.contains("WHEAT")) {
            setPose(stand,
                    new float[]{10f, 0f, 0f},     // Kafa: Yiyeceğe/Alete bakar
                    defaultLeftArm,
                    new float[]{-50f, -10f, 0f},  // Sağ Kol: Ağza götürür gibi değil, sunar gibi göğüs hizasında
                    new float[]{5f, 0f, -10f}, new float[]{-5f, 0f, 10f}
            );
            stand.getEquipment().setItemInMainHand(item);
            setArmor(stand, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, UUID_DEFAULT, Color.GREEN);
        }
        // 7. VARSAYILAN (Tüccar Duruşu: Buyrun der gibi)
        else {
            setPose(stand,
                    new float[]{0f, 0f, 0f},
                    new float[]{-20f, 0f, -20f}, // Sol Kol: Hafif açık
                    new float[]{-90f, 10f, 0f},  // Sağ Kol: Tam karşıya uzatılmış (Avuç içi yukarıda gibi)
                    new float[]{5f, 0f, -10f}, new float[]{-5f, 0f, 10f}
            );
            stand.getEquipment().setItemInMainHand(item);
            setArmor(stand, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, UUID_DEFAULT, Color.WHITE);
        }
    }

    private void setPose(ArmorStand stand, float[] head, float[] lArm, float[] rArm, float[] lLeg, float[] rLeg) {
        stand.setHeadPose(toEuler(head));
        stand.setLeftArmPose(toEuler(lArm));
        stand.setRightArmPose(toEuler(rArm));
        stand.setLeftLegPose(toEuler(lLeg));
        stand.setRightLegPose(toEuler(rLeg));
    }

    private EulerAngle toEuler(float[] deg) {
        return new EulerAngle(Math.toRadians(deg[0]), Math.toRadians(deg[1]), Math.toRadians(deg[2]));
    }

    private void setArmor(ArmorStand stand, Material chest, Material legs, Material boots, UUID headUUID, Color color) {
        ItemStack c = new ItemStack(chest);
        ItemStack l = new ItemStack(legs);
        ItemStack b = new ItemStack(boots);

        if (color != null) {
            if (c.getItemMeta() instanceof LeatherArmorMeta m) { m.setColor(color); c.setItemMeta(m); }
            if (l.getItemMeta() instanceof LeatherArmorMeta m) { m.setColor(color); l.setItemMeta(m); }
            if (b.getItemMeta() instanceof LeatherArmorMeta m) { m.setColor(color); b.setItemMeta(m); }
        }

        stand.getEquipment().setChestplate(c);
        stand.getEquipment().setLeggings(l);
        stand.getEquipment().setBoots(b);
        stand.getEquipment().setHelmet(getHeadFromUUID(headUUID));
    }

    private ItemStack getHeadFromUUID(UUID uuid) {
        // Cache kontrolü - Performans için kritik
        if (headCache.containsKey(uuid)) {
            return headCache.get(uuid).clone();
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            // OfflinePlayer çağrısı bir kez yapılır ve saklanır
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            head.setItemMeta(meta);
        }

        headCache.put(uuid, head);
        return head;
    }

    public void removeDisplay(Location loc) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> removeDisplay(loc));
            return;
        }

        if (activeArmorStands.containsKey(loc)) {
            Entity[] entities = activeArmorStands.get(loc);
            for (Entity e : entities) {
                if (e instanceof ArmorStand stand) {
                    returnArmorStandToPool(stand);
                } else if (e != null) {
                    e.remove();
                }
            }
            activeArmorStands.remove(loc);
        }

        if (activeHolograms.containsKey(loc)) {
            Hologram holo = activeHolograms.get(loc);
            if (holo != null) {
                DHAPI.removeHologram(holo.getName());
            }
            activeHolograms.remove(loc);
        }
    }
}